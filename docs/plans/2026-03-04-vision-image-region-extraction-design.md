# 多模态题目图像提取方案设计

> **日期**: 2026-03-04 (2026-03-04 v2 更新: OCR Bbox 方案替代 Vision 模型)  
> **目标**: 在现有 OCR + LLM 管道中增加图像区域提取能力，从试题截图中自动提取题干配图，实现多模态题目入库。

---

## 0. 方案变更说明 (v2)

### 0.1 原方案 (v1): Vision 模型

原设计使用 `glm-4v-plus` 视觉模型并行分析图片，输出千分位 bbox 坐标，再裁剪图片。

**放弃原因**:
1. **OCR 已经返回 bbox** — `layout_parsing` 的 `md_results` 包含 `![](page=0,bbox=[x1,y1,x2,y2])` 形式的像素级坐标
2. **精度天差地别** — OCR bbox 是像素级精确坐标；Vision 模型是千分位近似值 (±10-20% 偏差)
3. **零额外开销** — 无需额外 API 调用、无额外延迟、无额外 token 消耗
4. **已验证可行** — `ocr_bbox_crop_test.py` 成功从真实试卷图片裁剪出 2 个图形区域

### 0.2 新方案 (v2): OCR Bbox 裁剪

直接从 OCR `layout_parsing` 返回的 `md_results` 中解析 `![](page=N,bbox=[x1,y1,x2,y2])` 标记，用原图像素坐标裁剪，零额外 API 调用。

### 0.3 额外修复: 模型配置分离

**问题诊断**: StemXmlConverter 使用 GLM-5 (深度推理模型) 导致：
- 推理耗时 60-171s，reasoning_content 消耗 7000-9000 tokens
- 有时 token 预算耗尽 → content 为空 → xml_len=0
- GLM-5 **无法关闭深度思考** (测试 `thinking: {enabled: false}` 和 `do_sample: false` 均无效)

**解决方案**: 分离模型配置
| 组件 | 模型 | 配置前缀 | 理由 |
|------|------|---------|------|
| StemXmlConverter | `glm-4-0520` | `stemxml.*` | 格式转换不需要推理，5.3s，输出稳定 |
| AiAnalysisTaskConsumer | `glm-5` | `zhipuai.*` | AI 分析 (标签/难度/思路) 受益于深度推理 |

**模型对比测试数据** (同一 OCR 文本 + 完整 StemXml prompt):

| 模型 | 耗时 | content 长度 | reasoning 长度 | XML 质量 |
|------|------|-------------|---------------|---------|
| **glm-4-0520** | **5.3s** | 710 | 0 | ✅ 完美 |
| glm-4-plus | 6.0s | 709 | 0 | ✅ 完美 |
| glm-4-air | 8.1s | 709 | 0 | ✅ 完美 |
| glm-4-flash | 50.0s | 711 | 0 | ✅ 完美 |
| glm-5 | 60-171s | 0-710 | 7000-9000 | ❌ 不稳定 |

---

## 1. 现状分析

### 1.1 当前管道

```
┌──────────────────────────────────────────────────────────────────────┐
│  Stage 1: glm-ocr (layout_parsing)                                   │
│  Image → REST API → md_results (含 LaTeX + bbox 标记)               │
│  ⚠ Prompt 明确要求 "IGNORE: all images, graphs, geometry figures"     │
│  💡 但 md_results 中仍返回了 ![](page=0,bbox=[x1,y1,x2,y2]) 标记    │
├──────────────────────────────────────────────────────────────────────┤
│  Stage 1.5: glm-4-0520 (StemXmlConverter) ← 从 glm-5 切换          │
│  纯文本 → stem XML (<stem>, <choices>, <blank>, etc.)                │
│  💡 现在使用非推理模型，5.3s 稳定输出                                  │
├──────────────────────────────────────────────────────────────────────┤
│  Stage 2: glm-5 (AiAnalysisTaskConsumer)                             │
│  stem XML → tags + difficulty + reasoning                            │
│  ✅ 保留深度推理，适合分析任务                                         │
└──────────────────────────────────────────────────────────────────────┘
```

### 1.2 OCR 返回的 bbox 示例

```markdown
某些题目文本...
![](page=0,bbox=[226, 241, 419, 364])
<div align="center">
图1
</div>
更多文本
![](page=0,bbox=[463, 247, 656, 358])
<div align="center">
图2
</div>
```

**关键发现**: `bbox=[x1, y1, x2, y2]` 是**像素级坐标**，直接对应原图像素位置。

### 1.3 Bbox 裁剪验证结果

使用 `ocr_bbox_crop_test.py` 对真实试卷图片 (881×426px) 测试：

| 图片 | bbox | 裁剪尺寸 | 内容 |
|------|------|---------|------|
| 图1 | (226,241,419,364) | 193×123 px | 举架结构照片 ✅ |
| 图2 | (463,247,656,358) | 193×111 px | 截面示意图 ✅ |

裁剪精度：**像素级精确**，无需 padding 补偿。

### 1.4 数据库现状（已有，可复用）

| 表 | 字段 | 说明 |
|---|---|---|
| `q_question_asset` | `asset_type` | 已支持 `STEM_IMAGE`, `CHOICE_IMAGE`, `INLINE_IMAGE` |
| `q_question_asset` | `ref_key` | XML 引用 key，如 `fig-1`, `fig-2` |
| `q_question_asset` | `image_data` | base64 编码（MEDIUMTEXT，≤40KB/张） |
| `q_question` | `stem_image_id` | FK → `q_question_asset.id`，题干配图 |

---

## 2. 新管道架构 (v2)

### 2.1 流程图

```
User uploads image
       │
       ▼
question-service: createOcrTask(imageBase64) ──MQ──▶ ocr-service
       │                                                    │
       │                                         ┌──────────┴──────────┐
       │                                         │ OcrTaskConsumer     │
       │                                         │  onTaskCreated()    │
       │                                         └──────────┬──────────┘
       │                                                    │
       │                              ┌─────────────────────┘
       │                              ▼
       │                    ┌──────────────────┐
       │                    │ Stage 1: OCR     │
       │                    │ glm-ocr          │
       │                    │ → md_results     │
       │                    │   (含 bbox 标记) │
       │                    └────────┬─────────┘
       │                             │
       │                             ▼
       │                    ┌──────────────────────┐
       │                    │ Stage 1a: Preprocess  │  ← NEW
       │                    │ OcrTextPreprocessor   │
       │                    │ → cleanedText         │
       │                    │ → BboxRegion[]        │
       │                    └────────┬─────────────┘
       │                             │
       │                             ▼
       │                    ┌──────────────────────┐
       │                    │ Stage 1.5: StemXml   │  ← MODEL CHANGED
       │                    │ glm-4-0520           │
       │                    │ cleanedText → XML    │
       │                    │ (含 <image> 占位符)   │
       │                    └────────┬─────────────┘
       │                             │
       │                             ▼
       │                    ┌──────────────────────┐
       │                    │ Stage 1b: Crop       │  ← FUTURE
       │                    │ BufferedImage crop    │
       │                    │ bbox → CroppedImage[] │
       │                    └────────┬─────────────┘
       │                             │
       │                             ▼
       │                    ┌──────────────────────┐
       │                    │ Publish Result        │
       │                    │ stemXml              │
       │                    │ + croppedImages[]    │
       │                    └────────┬─────────────┘
       │                             │
       ◄─────────────────────────────┘
       │  OcrTaskResultEvent
       ▼
question-service: OcrResultConsumer
  1. Save stemXml
  2. Save croppedImages[] → q_question_asset    ← FUTURE
  3. Link stem_image_id → q_question            ← FUTURE
  4. Redis + WebSocket push
```

### 2.2 阶段详解

#### Stage 1a: OCR 文本预处理 (NEW — 已实现)

| 项目 | 值 |
|------|---|
| 实现类 | `OcrTextPreprocessor` (ocr-service/client) |
| 输入 | OCR `md_results` 原始文本 |
| 输出 | `PreprocessResult(cleanedText, List<BboxRegion>)` |

**处理逻辑**:
1. 解析 `![](page=N,bbox=[x1,y1,x2,y2])` → 提取 `BboxRegion` 列表
2. 替换 bbox 标记为 `<image ref="fig-N" bbox="x1,y1,x2,y2" />`
3. 移除 `<div align="center">图N</div>` 标签块
4. 清理多余空行

```java
public record BboxRegion(int index, int page, int x1, int y1, int x2, int y2) {
    public String toBboxString() { return x1 + "," + y1 + "," + x2 + "," + y2; }
}
```

#### Stage 1.5: StemXmlConverter (MODEL CHANGED — 已实现)

| 项目 | 旧值 | 新值 |
|------|------|------|
| 模型 | `glm-5` (深度推理) | `glm-4-0520` (快速稳定) |
| 配置前缀 | `zhipuai.*` | `stemxml.*` |
| 耗时 | 60-171s | 5.3s |
| 空内容处理 | 静默返回 "" | 重试 2 次后抛异常 |

#### Stage 1b: 图像裁剪 (FUTURE — 待实现)

| 项目 | 值 |
|------|---|
| 实现类 | `ImageRegionCropper` (待创建) |
| 输入 | `imageBase64` + `List<BboxRegion>` |
| 输出 | `List<CroppedImage>` |
| 裁剪方式 | 像素坐标直接裁剪 (无需 padding，OCR bbox 是精确像素坐标) |
| 格式 | PNG base64 |

```java
record CroppedImage(
    String refKey,      // "fig-1", "fig-2"
    String imageBase64, // PNG base64
    String mimeType,    // "image/png"
    int width, int height
) {}
```

---

## 3. 已实现的代码变更

### 3.1 新增文件

| 文件 | 说明 |
|------|------|
| `StemXmlProperties.java` | `@ConfigurationProperties(prefix = "stemxml")`，独立模型配置 |
| `OcrTextPreprocessor.java` | OCR 文本预处理：bbox 解析 + Markdown 清理 |
| `OcrTextPreprocessorTest.java` | 6 个单元测试 |

### 3.2 修改文件

| 文件 | 变更内容 |
|------|---------|
| `StemXmlConverter.java` | 使用 `StemXmlProperties` 替代 `ZhipuAiProperties`；新增空内容重试 (MAX_RETRIES=2)；空内容抛异常而非静默返回 |
| `OcrTaskConsumer.java` | 注入 `OcrTextPreprocessor`；QUESTION_STEM 先预处理再转 XML；空结果检测 → FAILED |
| `ZhipuAiConfig.java` | 注册 `StemXmlProperties` |
| `application.yml` | 新增 `stemxml:` 配置节 (`model: glm-4-0520`, `max-tokens: 4096`) |
| `docker-compose.yml` | 新增 `STEMXML_MODEL: glm-4-0520` |
| `docker-compose.dev.yml` | 新增 `STEMXML_MODEL: glm-4-0520` |
| `StemXmlConverterTest.java` | 改用 `StemXmlProperties`；新增空内容/null 重试测试 |

### 3.3 Bug 修复

| Bug | 根因 | 修复 |
|-----|------|------|
| `xml_len=0` 但标记 SUCCESS | StemXmlConverter 静默返回 ""，OcrTaskConsumer 不检查 | StemXmlConverter 重试 2 次后抛异常；OcrTaskConsumer 检测空结果标记 FAILED |
| GLM-5 content 为空 | 深度推理耗尽 token 预算 | 切换 StemXmlConverter 到 glm-4-0520 (非推理模型) |

---

## 4. 配置说明

### 4.1 application.yml

```yaml
# AI 分析专用（GLM-5 深度推理）
zhipuai:
  api-key: ${ZHIPUAI_API_KEY:}
  model: ${ZHIPUAI_MODEL:glm-5}
  temperature: 0.1
  max-tokens: 65536

# StemXml 转换专用（快速非推理模型）
stemxml:
  model: ${STEMXML_MODEL:glm-4-0520}
  temperature: 0.1
  max-tokens: 4096
```

### 4.2 环境变量

| 变量 | 默认值 | 说明 |
|------|-------|------|
| `ZHIPUAI_MODEL` | `glm-5` | AI 分析模型（深度推理） |
| `STEMXML_MODEL` | `glm-4-0520` | StemXml 转换模型（快速稳定） |
| `ZHIPUAI_API_KEY` | — | 共享 API Key |

---

## 5. 后续计划

### Phase 2: 图像裁剪落库 (Next)

1. 新增 `ImageRegionCropper` — Java BufferedImage 像素裁剪
2. 扩展 `OcrTaskResultEvent` — 增加 `extractedImages` 字段
3. 修改 `OcrResultConsumer` — 存储裁剪图片到 `q_question_asset`
4. 修改 `DbWriteBackEvent` — 增加图片数据传输

### Phase 3: 前端渲染

1. `stem-xml.js` — 渲染 `<image ref="fig-1" />` 为 `<img>` 标签
2. `renderer.js` — 从 API 加载 QuestionAsset 图片数据

### Phase 4: Nacos 热配置

```yaml
stemxml:
  model: glm-4-0520     # 可热切换
  temperature: 0.1
  max-tokens: 4096
```

---

## 6. v1 vs v2 对比

| 维度 | v1 (Vision 模型) | v2 (OCR Bbox) |
|------|-----------------|---------------|
| 额外 API 调用 | 1 次 (glm-4v-plus) | **0 次** |
| 额外延迟 | +5-8s | **0s** |
| 额外 token 消耗 | ~3K tokens/题 | **0** |
| bbox 精度 | 千分位, ±10-20% | **像素级** |
| 需要 padding | 是 (10% 外扩) | **不需要** |
| 实现复杂度 | 高 (新 Client + Prompt + 并行) | **低 (正则解析 + 裁剪)** |
| 图片分类 | 自动 (stem/option/inline) | **需人工或规则判断** |
| 鲁棒性 | 视觉模型可能漏检 | **OCR 已返回即存在** |

**结论**: v2 在精度、性能、成本上全面优于 v1，唯一不足是缺乏自动分类（stem_image vs option_image），但可通过位置规则或后续 LLM 分析补充。

---

## 7. 测试脚本

```powershell
# Bbox 裁剪可视化测试
python backend/scripts/ocr_bbox_crop_test.py
# → bbox_crop_output/result.html

# 模型对比测试
$env:ZHIPUAI_API_KEY="your-key"
python backend/scripts/test_model_comparison.py

# Vision 模型测试 (v1 方案, 仅供参考)
python backend/scripts/glm_vision_image_region_test.py --demo --model glm-4v-plus
```
