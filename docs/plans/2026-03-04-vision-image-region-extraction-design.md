# 多模态题目图像提取方案设计

> **日期**: 2026-03-04  
> **目标**: 在现有 OCR + LLM 管道中增加"视觉模型图像区域识别"能力，从试题截图中自动提取题干配图、选项图、题内图，实现真正的多模态题目入库。

---

## 1. 现状分析

### 1.1 当前管道

```
┌──────────────────────────────────────────────────────────────────────┐
│  Stage 1: glm-ocr (layout_parsing)                                   │
│  Image → REST API → 纯文本 (含 LaTeX)                                │
│  ⚠ Prompt 明确要求 "IGNORE: all images, graphs, geometry figures"     │
├──────────────────────────────────────────────────────────────────────┤
│  Stage 1.5: glm-5 (StemXmlConverter)                                 │
│  纯文本 → stem XML (<stem>, <choices>, <blank>, etc.)                │
│  ⚠ 虽然 schema 允许 <image ref="original"/>，但无图片数据来源          │
├──────────────────────────────────────────────────────────────────────┤
│  Stage 2: glm-5 (AiAnalysisTaskConsumer)                             │
│  stem XML → tags + difficulty + reasoning                            │
│  ⚠ 在 buildUserPrompt 中主动去除 <image/> 标签                       │
└──────────────────────────────────────────────────────────────────────┘
```

**核心缺陷**: 图片中的图形/配图被完全丢弃，只保留了文字。

### 1.2 数据库现状（已有，可复用）

| 表 | 字段 | 说明 |
|---|---|---|
| `q_question_asset` | `asset_type` | 已支持 `STEM_IMAGE`, `CHOICE_IMAGE`, `INLINE_IMAGE` |
| `q_question_asset` | `ref_key` | XML 引用 key，如 `img-1`, `opt-A` |
| `q_question_asset` | `image_data` | base64 编码（MEDIUMTEXT，≤40KB/张） |
| `q_question` | `stem_image_id` | FK → `q_question_asset.id`，题干配图 |

现有存储层 **完全够用**，无需新增表。

---

## 2. 视觉模型测试结论

### 2.1 测试方法

使用 Python 脚本 `glm_vision_image_region_test.py` 向 GLM 视觉模型发送合成试题图片，要求返回图像区域的 JSON 坐标。

### 2.2 测试结果对比

| 模型 | 区域数 | 分类正确性 | 描述质量 | 坐标精度 | 延迟 |
|------|--------|-----------|---------|---------|------|
| **glm-4v-plus** | 5/5 ✅ | 全部正确 | 好（圆形方形/抛物线/正弦波/柱状图） | 方向正确，偏移±10-20% | 5-7s |
| **glm-4v-flash** | 5/5 ✅ | 全部正确 | **最佳**（精确描述每种图形） | 方向正确，偏移±10-20% | 6s |
| glm-4v | 3/5 ❌ | 漏检 B/C | 中等 | 较差 | 9s |

### 2.3 关键发现

1. **区域分类能力强** — 模型能正确区分 stem_image / option_image / inline_image  
2. **内容理解优秀** — 能识别"抛物线""正弦曲线""柱状图"等图形类型  
3. **坐标为近似值** — 千分位偏差约 ±100-200（即 ±10%-20%），无法做到像素级精确  
4. **多行布局易误判** — 2×2 网格的选项图可能被识别为 1×4 排列

### 2.4 推荐模型

| 场景 | 推荐模型 | 理由 |
|------|---------|------|
| 生产 | `glm-4v-plus` | 最高检出率，稳定 |
| 降本 | `glm-4v-flash` | 同样全检出，描述更佳，token 更少 |

---

## 3. 整体流程设计

### 3.1 新管道架构

```
User uploads image
       │
       ▼
question-service: createOcrTask(imageBase64) ──Feign──▶ ocr-service
       │                                                    │
       │                                         ┌──────────┴──────────┐
       │                                         │ OcrTaskConsumer     │
       │                                         │  onTaskCreated()    │
       │                                         └──────────┬──────────┘
       │                                                    │
       │                              ┌─────────────────────┼─────────────────────┐
       │                              │ PARALLEL            │                     │
       │                              ▼                     ▼                     │
       │                    ┌──────────────────┐  ┌──────────────────┐            │
       │                    │ Stage 1: OCR     │  │ Stage 1a: Vision │            │
       │                    │ glm-ocr          │  │ glm-4v-plus      │  ← NEW    │
       │                    │ → ocrText        │  │ → ImageRegion[]  │            │
       │                    └────────┬─────────┘  └────────┬─────────┘            │
       │                             │                     │                     │
       │                             │         ┌───────────┘                     │
       │                             │         ▼                                 │
       │                             │  ┌──────────────────┐                     │
       │                             │  │ Stage 1b: Crop   │  ← NEW             │
       │                             │  │ BufferedImage     │                     │
       │                             │  │ → CroppedImage[] │                     │
       │                             │  └────────┬─────────┘                     │
       │                             │           │                               │
       │                             ▼           ▼                               │
       │                    ┌──────────────────────────────┐                     │
       │                    │ Stage 1.5: StemXml + Images  │  ← ENHANCED        │
       │                    │ glm-5 / glm-4v-plus          │                     │
       │                    │ ocrText + imageRegions        │                     │
       │                    │ → stem XML with <image ref>  │                     │
       │                    └────────────────┬─────────────┘                     │
       │                                     │                                   │
       │                                     ▼                                   │
       │                    ┌──────────────────────────────┐                     │
       │                    │ Publish Result               │                     │
       │                    │ stemXml + croppedImages[]     │                     │
       │                    └────────────────┬─────────────┘                     │
       │                                     │                                   │
       ◄─────────────────────────────────────┘                                   │
       │  OcrTaskResultEvent (enhanced)                                          │
       ▼                                                                         │
question-service: OcrResultConsumer                                              │
  1. Save stemXml                                                                │
  2. Save croppedImages[] → q_question_asset    ← NEW                           │
  3. Link stem_image_id → q_question            ← NEW                           │
  4. Redis + WebSocket push                                                      │
```

### 3.2 阶段详解

#### Stage 1a: 视觉模型图像区域分析（NEW）

| 项目 | 值 |
|------|---|
| 实现类 | `VisionImageRegionClient` (ocr-service) |
| 模型 | `glm-4v-plus` (可通过 Nacos 热配切换) |
| 输入 | `imageBase64` (原始扫描图) |
| 输出 | `List<ImageRegion>` |
| API | Chat Completion (非 layout_parsing) |
| Prompt | 见下方 §4 |

```java
// 输出数据结构
record ImageRegion(
    String type,        // "stem_image" | "option_image" | "inline_image"
    String key,         // option: "A"/"B"/"C"/"D"; inline: "1"/"2"; stem: null
    int[] bbox,         // [x1, y1, x2, y2] 千分位坐标 0~1000
    String description  // 图像内容简述
) {}
```

#### Stage 1b: 图像裁剪（NEW）

| 项目 | 值 |
|------|---|
| 实现类 | `ImageRegionCropper` (ocr-service) |
| 输入 | `imageBase64` + `List<ImageRegion>` |
| 输出 | `List<CroppedImage>` |
| 裁剪策略 | 千分位→像素 + **10%外扩 padding**（补偿视觉模型坐标偏差）|
| 格式 | 输出 PNG base64 |
| 大小限制 | 单张裁剪后 > 50KB 时降质/缩放 |

```java
record CroppedImage(
    String type,        // 同 ImageRegion.type
    String refKey,      // "stem-img", "opt-A", "opt-B", "inline-1", ...
    String imageBase64, // 裁剪后的 PNG base64
    String mimeType,    // "image/png"
    String description  // 来自视觉模型的描述
) {}
```

**裁剪伪代码:**
```python
def crop_with_padding(image, bbox_1000, padding_ratio=0.1):
    w, h = image.size
    x1 = bbox[0] / 1000 * w
    y1 = bbox[1] / 1000 * h
    x2 = bbox[2] / 1000 * w
    y2 = bbox[3] / 1000 * h
    
    # 外扩 padding
    pw = (x2 - x1) * padding_ratio
    ph = (y2 - y1) * padding_ratio
    x1 = max(0, x1 - pw)
    y1 = max(0, y1 - ph)
    x2 = min(w, x2 + pw)
    y2 = min(h, y2 + ph)
    
    return image.crop(x1, y1, x2, y2)
```

#### Stage 1.5 增强: StemXmlConverter

**改动**: 新增 `convertToStemXml(ocrText, List<ImageRegion> regions)` 重载方法。

在 prompt 中追加图像区域信息，让 GLM-5 在生成 XML 时正确放置 `<image>` 标签：

```
=== 可用图像区域 ===
本题包含以下图像(由视觉模型自动识别):
- stem_image: "直角三角形ABC示意图" → 请在 <stem> 下放置 <image ref="stem-img" />
- option_image key=A: "圆形和方形组合" → 请在 <choice key="A"> 中放置 <image ref="opt-A" />
- option_image key=B: "抛物线" → <choice key="B"> 中放置 <image ref="opt-B" />
...
```

生成的 stem XML 示例：
```xml
<stem version="1">
  <p>如图所示，在△ABC中，AB = 3，BC = 4，∠ABC = 90°。</p>
  <image ref="stem-img" />
  <choices mode="single">
    <choice key="A">
      <image ref="opt-A" />
    </choice>
    <choice key="B">
      <image ref="opt-B" />
    </choice>
    <choice key="C">
      <image ref="opt-C" />
    </choice>
    <choice key="D">
      <image ref="opt-D" />
    </choice>
  </choices>
</stem>
```

### 3.3 MQ 消息扩展

#### OcrTaskResultEvent（增强）

```java
public record OcrTaskResultEvent(
    String taskUuid,
    String bizType,
    String bizId,
    String status,
    String recognizedText,     // stem XML (已含 <image ref> 标签)
    String errorCode,
    String errorMessage,
    String requestUser,
    String finishedAt,
    // ===== NEW FIELDS =====
    List<ExtractedImage> extractedImages  // 裁剪后的图片列表（可为 null/empty）
) {}

record ExtractedImage(
    String type,        // "STEM_IMAGE" / "CHOICE_IMAGE" / "INLINE_IMAGE"
    String refKey,      // "stem-img", "opt-A", "inline-1"
    String imageBase64, // PNG base64
    String mimeType,    // "image/png"
    String description  // 图形描述（可选）
) {}
```

> **向后兼容**: 旧消息 `extractedImages` 为 null，Deserializer 用 `@JsonIgnoreProperties(ignoreUnknown=true)` 处理。

#### DbWriteBackEvent 扩展

新增工厂方法 `ocrWithImages()`:
```java
public static DbWriteBackEvent ocrWithImages(
    String taskUuid, String questionUuid, String status,
    String userId, String bizType, String recognizedText,
    String extractedImagesJson, // JSON array of ExtractedImage
    String errorMsg
) { ... }
```

persist-service 的 `DbPersistConsumer` 增加逻辑：解析 `extractedImagesJson`，批量 insert 到 `q_question_asset`。

---

## 4. Prompt 设计

### 4.1 视觉模型 Prompt (Stage 1a)

```text
你是一个试题图像分析引擎。你的任务是分析一张中国高考/考试数学试题的截图，
识别其中所有嵌入的图像/图形区域（非文字部分），并返回每个区域的边界框坐标。

## 图像分类

图像区域分为三类：
1. stem_image — 题干配图（几何图形、函数图像、数据表格等）。至多一张。
2. option_image — 选项图（出现在 A/B/C/D 中的图片）。用 key 标记。
3. inline_image — 题内图（除上述两类之外的其他图像）。用序号 id 标记。

## 坐标格式

使用千分位坐标（0~1000 的整数），表示相对于整张图片宽高的比例：
- x1, y1 = 左上角
- x2, y2 = 右下角

## 输出格式

严格输出以下 JSON（无 markdown、无解释）：
{
  "regions": [
    {"type": "stem_image", "bbox": [x1, y1, x2, y2], "description": "简述"},
    {"type": "option_image", "key": "A", "bbox": [...], "description": "简述"},
    {"type": "inline_image", "id": 1, "bbox": [...], "description": "简述"}
  ]
}

## 规则
1. 只识别非文字的图像/图形区域。纯文字、公式不算图像。
2. 边界框紧密包围图像内容，不含周围文字。
3. 如果选项含图片，只框选图片部分。
4. 没有图像区域则: {"regions": []}
5. stem_image 至多一个。
6. 坐标为 0~1000 整数。
7. 只输出 JSON。
```

### 4.2 StemXmlConverter 增强 Prompt（追加到 USER message）

```text
{ocrText}

=== 可用图像区域（由视觉分析自动识别）===
{foreach region in regions:}
- {region.type}{if option: key={key}}{if inline: id={id}}: "{description}" → 使用 <image ref="{refKey}" />
{end}

请在生成的 XML 中，将上述图像插入到对应位置。
stem_image → <stem> 下直接放 <image ref="stem-img" />
option_image → 对应 <choice> 内放 <image ref="opt-{key}" />
inline_image → 对应段落 <p> 附近放 <image ref="inline-{id}" />
```

---

## 5. 存储方案

### 5.1 数据流转

```
ocr-service                          question-service
┌─────────────┐     MQ Event         ┌─────────────────────────────┐
│ Crop images │ ──────────────────▶  │ OcrResultConsumer            │
│ (base64 PNG)│   extractedImages[]  │  ├─ Save stemXml             │
│             │                      │  ├─ forEach extractedImage:  │
│             │                      │  │   ├─ Upsert QuestionAsset │
│             │                      │  │   │  (refKey, base64, type)│
│             │                      │  │   └─ If STEM_IMAGE:       │
│             │                      │  │      set question.stem_image_id│
│             │                      │  ├─ WebSocket push           │
│             │                      │  └─ DbWriteBack (images)     │
└─────────────┘                      └─────────────────────────────┘
```

### 5.2 QuestionAsset 存储映射

| 视觉模型 type | QuestionAsset.asset_type | ref_key 命名 | 备注 |
|---|---|---|---|
| `stem_image` | `STEM_IMAGE` | `stem-img` | 同时设置 `q_question.stem_image_id` |
| `option_image` key=A | `CHOICE_IMAGE` | `opt-A` | 选项 A 的配图 |
| `option_image` key=B | `CHOICE_IMAGE` | `opt-B` | 选项 B 的配图 |
| `inline_image` id=1 | `INLINE_IMAGE` | `inline-1` | 题内图 #1 |

### 5.3 Stem XML 引用

前端渲染 stem XML 时，遇到 `<image ref="opt-A" />`，通过 ref_key 查找对应的 QuestionAsset 获取 base64 图像数据：

```
GET /api/questions/{uuid}/assets → QuestionAssetResponse[]
  { assetUuid, refKey: "opt-A", imageData: "iVBOR...", mimeType: "image/png" }
```

**前端已有 stem-xml.js 解析器，只需增加 `<image>` 标签的渲染逻辑。**

---

## 6. 实现计划

### 6.1 Phase 1: 核心管道（ocr-service）

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 1 | 新增 `VisionImageRegionClient` | ocr-service/client/ | 调用 glm-4v-plus Chat API，解析返回 JSON |
| 2 | 新增 `ImageRegionCropper` | ocr-service/client/ | Java BufferedImage 裁剪 + padding + 压缩 |
| 3 | 新增配置 `VisionModelProperties` | ocr-service/config/ | endpoint, model, api-key, padding-ratio |
| 4 | 修改 `OcrTaskConsumer` | ocr-service/mq/ | 在 OCR 完成后parallel调用 vision + crop |
| 5 | 增强 `StemXmlConverter` | ocr-service/client/ | 接受 imageRegions 参数，追加到 prompt |

### 6.2 Phase 2: MQ 消息 & 消费（common-contract + question-service）

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 6 | 新增 `ExtractedImage` record | common-contract/contract/ | 图片传输 DTO |
| 7 | 扩展 `OcrTaskResultEvent` | common-contract/contract/ | 增加 extractedImages 字段 |
| 8 | 修改 `OcrResultConsumer` | question-service/mq/ | 接收并存储 extractedImages |
| 9 | 修改 `DbWriteBackEvent` | common-contract/contract/ | 增加 extractedImagesJson |

### 6.3 Phase 3: 前端渲染

| # | 任务 | 文件 | 说明 |
|---|------|------|------|
| 10 | 增强 `stem-xml.js` | frontend/src/ | `<image ref>` → `<img src="data:...">` |
| 11 | 增强 `renderer.js` | frontend/src/ | 加载 QuestionAsset 图片数据 |

### 6.4 Phase 4: Nacos 热配置

```yaml
# question-service.yml (Nacos)
qforge:
  vision:
    enabled: true
    model: glm-4v-plus
    padding-ratio: 0.10       # 裁剪外扩比例
    max-crop-size-kb: 50      # 单张裁剪最大 KB
    max-regions: 10           # 最大识别区域数
```

---

## 7. 风险与应对

| 风险 | 影响 | 应对策略 |
|------|------|---------|
| 坐标不精确（±15%） | 裁剪区域包含少量周围文字/空白 | 10% padding + 内容感知裁剪（Phase 5） |
| 视觉模型漏检 | 某些图片未被识别 | 前端提供手动标注兜底 UI |
| API 超时/限流 | 增加一次视觉 API 调用延迟 | 与 OCR 并行执行；失败不阻塞 OCR 流程 |
| MQ 消息过大 | base64 图片增大消息体 | 单张 ≤50KB，总消息 ≤2MB（RabbitMQ 默认 128MB） |
| 选项图坐标合并 | 2×2 网格被识别为 1×4 | 后处理：根据 y 坐标差异自动分行 |

### 7.1 降级策略

```
Vision API 失败 → 跳过图像提取 → 退回纯文本流程（现有行为）
                → 记录 errorMsg → 支持后续重试
```

**核心原则**: 视觉分析是增强，非必须。失败时优雅降级，不影响 OCR 文本流程。

---

## 8. 性能估算

| 阶段 | 延迟 | 成本 (每题) |
|------|------|------------|
| Stage 1 OCR  | 3-5s | ~3K tokens |
| Stage 1a Vision (NEW) | 5-8s | ~3K tokens |
| Stage 1b Crop | <100ms | CPU only |
| Stage 1.5 StemXml | 3-5s | ~2K tokens |
| **总计 (并行)** | **~8-12s** | **~8K tokens** |

当前（无 Vision）: ~6-10s, ~5K tokens  
增量: **+2-3s, +3K tokens/题**（因为 Stage 1 和 1a 并行执行）

---

## 9. 测试脚本使用说明

```powershell
# 1. 生成合成测试图片
python backend/scripts/generate_test_question_image.py

# 2. 运行视觉模型测试
$env:ZHIPUAI_API_KEY="your-key"
python backend/scripts/glm_vision_image_region_test.py `
    --file backend/scripts/test_question_with_figures.png `
    --model glm-4v-plus `
    --save-viz

# 3. 查看可视化结果
# → test_question_with_figures_regions.png（原图上标注边界框）
```

支持的参数：
- `--file <path>` — 本地图片
- `--url <url>` — 远程图片
- `--demo` — 智谱官方示例
- `--model <name>` — 模型名（推荐 glm-4v-plus / glm-4v-flash）
- `--save-viz` — 保存可视化结果
