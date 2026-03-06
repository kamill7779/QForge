# 试卷自动解析综合设计文档

**日期:** 2026-03-06  
**状态:** 设计稿（已通过可行性评估）  
**关联文档:**
- [PDF 自动解析 Agent 设计（前版本）](2026-03-05-pdf-auto-parse-agent-design.md)
- [答案 OCR 资产流水线实现](2026-03-04-answer-ocr-asset-pipeline-implementation.md)
- [图片资产存储方案](2026-03-04-image-asset-storage-design.md)
- [XML 题干存储方案](2026-03-02-xml-stem-storage-design.md)
- [当前数据库 Schema](../current-database-schema.md)

---

## 1. 设计目标与约束

### 1.1 支持的输入形式

| 媒体形式 | 说明 |
|----------|------|
| 单 PDF | 单页或多页扫描件 / 电子排版件 |
| 多 PDF | 多份文件构成一套试卷（如试题 + 答案分卷） |
| 单图 / 多图 | 手机拍摄的照片或截图（JPEG/PNG/WEBP） |
| 组合输入 | 部分页为 PDF、部分为图片 |

| 内容形式 | 说明 |
|----------|------|
| 仅题目 | 无答案，只解析题干 |
| 题目 + 答案（紧随型） | 每道题下方紧接答案与解析 |
| 题目 + 答案（集中型） | 所有题目列完后，在文末集中列答案 |
| 题目 + 答案（分文件型） | 题目与答案分属不同文件（多文件场景） |
| 有解析 / 无解析 | 答案可能含解题过程，也可能仅有结论 |

**核心原则：** 系统不对内容形式做任何预设；由 LLM 根据 OCR 全文进行推断和匹配。

### 1.2 设计约束

| 约束项 | 值 | 说明 |
|--------|----|------|
| 单次上传文件数量 | ≤ 10 | PDF + 图片合计 |
| 单次上传总大小 | ≤ 100 MB | |
| PDF 页数上限 | ≤ 100 页（所有 PDF 合计） | |
| 解析题目数上限 | ≤ 60 题 / 任务 | |
| OCR 单张图片大小 | ≤ 20 MB | GLM-OCR 限制 |
| LLM 上下文上限 | 128K tokens（GLM-Z-Plus 约 16 万汉字） | 超出则分块 |
| 图片 base64 存储 | 沿用现有方案：题干图 ≤ 30 KB，答案图 ≤ 30 KB | 必要时压缩 |

### 1.3 非目标（本期不做）

- Word / DOCX 格式支持
- 实时协同编辑解析结果
- 跨任务题目去重
- 批量导出
- 解析任务的暂停 / 恢复

---

## 2. 整体架构与服务职责

### 2.1 数据流总览

```
┌──────────────────┐   multipart/form-data (多文件)   ┌────────────────┐
│ Electron Frontend│ ──────────────────────────────► │gateway-service │
│                  │                                  │ (:8080)        │
│  ◄── WS Push ────┤                                  └───────┬────────┘
└──────────────────┘                                          │ route
                                                              ▼
                                            ┌─────────────────────────────┐
                                            │     question-service (:8089) │
                                            │                             │
                                            │  POST /api/exam-parse/tasks │
                                            │  → 创建 q_exam_parse_task   │
                                            │  → 存储源文件 base64         │
                                            │  → publish MQ               │
                                            └──────────────┬──────────────┘
                                                           │ MQ: exam.parse.task.created
                                                           ▼
                                            ┌─────────────────────────────┐
                                            │       ocr-service (:8090)   │
                                            │                             │
                                            │ ExamParseTaskConsumer       │
                                            │  Step 1: PDF → 图片页       │
                                            │  Step 2: 逐页 GLM-OCR       │
                                            │  Step 3: 整合全文 + BBox 映射│
                                            │  Step 4: GLM-Z-Plus 拆题    │
                                            │          + 答案匹配          │
                                            │  Step 5: 逐题裁图           │
                                            │  Step 6: 逐题 StemXmlConverter│
                                            │  → publish MQ (逐题)        │
                                            └──────────────┬──────────────┘
                                                           │ MQ: exam.parse.result
                                                           ▼
                                            ┌─────────────────────────────┐
                                            │     question-service (:8089) │
                                            │                             │
                                            │ ExamParseResultConsumer     │
                                            │  → 创建 q_exam_parse_question│
                                            │  → WS 推送解析进度          │
                                            └─────────────────────────────┘
```

### 2.2 服务职责分工

| 服务 | 职责 |
|------|------|
| **gateway-service** | JWT 鉴权、路由、multipart 大文件支持（调整 `max-in-memory-size`） |
| **question-service** | 接受任务提交；存储源文件；创建解析任务记录；接收拆题结果；批量创建草稿题目；WebSocket 进度推送；用户确认触发最终入库 |
| **ocr-service** | PDF→图片（PDFBox）；多文件/多页 OCR（GLM-OCR）；OCR 文本整合；LLM 拆题+答案匹配（GLM-Z-Plus）；逐题图片裁剪；StemXmlConverter + AnswerXmlConverter |
| **persist-service** | 异步落库（沿用现有 `DbPersistConsumer` 扩展新 taskType `EXAM_PARSE` / `EXAM_PARSE_LOCAL`） |

---

## 3. 数据库设计

### 3.1 新增表

> 表名前缀 `q_exam_parse_` 已验证与现有 schema 无冲突。

#### `q_exam_parse_task`（解析任务主表）

```sql
CREATE TABLE IF NOT EXISTS q_exam_parse_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid       CHAR(36)     NOT NULL UNIQUE,
    owner_user      VARCHAR(128) NOT NULL,
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    -- PENDING / OCR_PROCESSING / SPLITTING / GENERATING / SUCCESS / PARTIAL_FAILED / FAILED
    progress        TINYINT      NOT NULL DEFAULT 0,   -- 0-100
    file_count      INT          NOT NULL DEFAULT 0,
    total_pages     INT          NOT NULL DEFAULT 0,
    question_count  INT          NOT NULL DEFAULT 0,
    has_answer_hint BOOLEAN      NOT NULL DEFAULT FALSE,
    error_msg       VARCHAR(2048)         NULL,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_owner_user (owner_user),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### `q_exam_parse_source_file`（源文件存储）

```sql
CREATE TABLE IF NOT EXISTS q_exam_parse_source_file (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid       CHAR(36)     NOT NULL,
    file_index      INT          NOT NULL,   -- 用户上传顺序，决定全局页码偏移
    file_name       VARCHAR(255) NOT NULL,
    file_type       VARCHAR(16)  NOT NULL,   -- PDF / IMAGE
    page_count      INT          NOT NULL DEFAULT 1, -- PDF 实际页数；IMAGE 固定为 1
    file_data       LONGTEXT     NOT NULL,   -- base64 原始文件
    ocr_status      VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    -- PENDING / PROCESSING / SUCCESS / FAILED
    INDEX idx_task_uuid (task_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

#### `q_exam_parse_question`（拆题结果暂存）

```sql
CREATE TABLE IF NOT EXISTS q_exam_parse_question (
    id                BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid         CHAR(36)     NOT NULL,
    seq_no            INT          NOT NULL,   -- 题目在试卷中的顺序（1-based）
    question_type     VARCHAR(32)  NULL,       -- SINGLE_CHOICE / MULTI_CHOICE / FILL_BLANK / SHORT_ANSWER / UNKNOWN
    raw_stem_text     LONGTEXT     NULL,       -- LLM 输出的原始题干文本（含图片占位符）
    stem_xml          LONGTEXT     NULL,       -- StemXmlConverter 输出
    raw_answer_text   LONGTEXT     NULL,       -- LLM 输出的原始答案文本
    answer_xml        LONGTEXT     NULL,       -- AnswerXmlConverter 输出
    stem_images_json  TEXT         NULL,       -- JSON: [{"ref":"img-1","imageData":"base64...","mimeType":"image/png"}]
    answer_images_json TEXT        NULL,       -- JSON: 同上
    source_pages      VARCHAR(255) NULL,       -- 来源页码范围，如 "0,1,2"
    question_uuid     CHAR(36)     NULL,       -- 用户确认后回填的 q_question.question_uuid
    confirm_status    VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
    -- PENDING / CONFIRMED / SKIPPED
    error_msg         VARCHAR(1024)         NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_uuid_seq (task_uuid, seq_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 4. REST API 设计

新增路由前缀：`/api/exam-parse`（位于 **question-service**，经 gateway 路由）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/exam-parse/tasks` | 上传文件、创建解析任务；multipart/form-data，files 字段（多文件） |
| `GET` | `/api/exam-parse/tasks` | 获取当前用户全部解析任务列表（分页） |
| `GET` | `/api/exam-parse/tasks/{taskUuid}` | 查询任务状态 + 进度 + 已解析题目列表 |
| `PUT` | `/api/exam-parse/tasks/{taskUuid}/questions/{seqNo}` | 前端编辑单道拆题结果（stem_xml / answer_xml / 图片） |
| `POST` | `/api/exam-parse/tasks/{taskUuid}/confirm` | 批量确认 → 为所有 PENDING 题目创建正式 `q_question` + `q_answer` + 资产记录 |
| `DELETE` | `/api/exam-parse/tasks/{taskUuid}` | 取消/删除解析任务及暂存数据 |

**`POST /api/exam-parse/tasks` 请求参数：**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `files` | `MultipartFile[]` | 是 | 上传文件，支持 .pdf / .jpg / .jpeg / .png / .webp，最多 10 个 |
| `hasAnswerHint` | `boolean` | 否 | 用户主动勾选"此试卷含答案"，作为 LLM 提示，默认 false |

**`POST /api/exam-parse/tasks` 响应（202 Accepted）：**

```json
{
  "taskUuid": "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
  "status": "PENDING",
  "fileCount": 3,
  "message": "任务已提交，请通过 WebSocket 监听进度"
}
```

### 4.1 WebSocket 事件扩展

复用现有 `/ws/ocr?user={username}` 连接及 `OcrWsPushService`（通用推送，event 为任意字符串），新增事件类型：

| 事件名 | payload 字段 | 说明 |
|--------|-------------|------|
| `exam.parse.progress` | `taskUuid, progress(0-100), stage(OCR/SPLITTING/GENERATING), pagesDone, totalPages` | 阶段性进度推送 |
| `exam.parse.question.ready` | `taskUuid, seqNo, stemXml, answerXml, stemImages[], answerImages[], questionType` | 单题解析完成，前端可逐题展示 |
| `exam.parse.succeeded` | `taskUuid, questionCount` | 全部拆题完成 |
| `exam.parse.failed` | `taskUuid, errorMessage, stage` | 全流程失败 |

> **已验证：** `OcrWsPushService.push(user, event, payload)` 中 event 为自由字符串，payload 为 `Map<String, Object>`，推送新事件类型零代码改动。

---

## 5. OCR 整合策略（多文件全局页码设计）

### 5.1 全局页码分配

多个上传文件按 `file_index` 顺序分配连续的全局页码（globalPage，0-based）：

```
file_index=0  (PDF, 3页)   → globalPage 0, 1, 2
file_index=1  (IMAGE, 1张) → globalPage 3
file_index=2  (PDF, 5页)   → globalPage 4, 5, 6, 7, 8
```

### 5.2 OCR 文本预处理与整合

每页 OCR 完成后，由新建的 **`ExamPagePreprocessor`** 类（不修改现有 `OcrTextPreprocessor`，避免影响单题 OCR 流程）执行预处理：

- 复用 `OcrTextPreprocessor` 中的 `BBOX_PATTERN`（已为 `public static` 可访问）解析 `![](page=N,bbox=[x1,y1,x2,y2])` 标记
- 替换为全局唯一 ref 格式：`<image ref="fig-{globalPage}-{figSeq}" bbox="{x1},{y1},{x2},{y2}" globalPage="{globalPage}" />`
- 每个 bbox 区域注册到全局图片 Registry（内存 HashMap）

OCR 整合文档格式（送入 LLM 前）：

```
=== PAGE 0 ===
（第0页 OCR 文本，图片占位符已替换为 <image ref="fig-0-1" .../> 形式）

=== PAGE 1 ===
（第1页 OCR 文本）

=== PAGE 3 ===
（单张图片的 OCR 文本）

...
```

页间分隔符 `=== PAGE N ===` 帮助 LLM 理解跨页题目的连续性关系。

### 5.3 PDF 转图处理链

```
PDF (base64)
  → PDFBox PDDocument.load()
  → 逐页 PDFRenderer.renderImageWithDPI(300)
  → 每页 BufferedImage → PNG base64（单页）
```

> **已验证：** Java 17 + PDFBox 3.0.2 完全兼容。依赖引入仅需在 `ocr-service/pom.xml` 添加：
> ```xml
> <dependency>
>     <groupId>org.apache.pdfbox</groupId>
>     <artifactId>pdfbox</artifactId>
>     <version>3.0.2</version>
> </dependency>
> ```

---

## 6. LLM 拆题策略

### 6.1 策略：一次性全文拆分

使用 **GLM-Z-Plus**（128K context window），将整合后的 OCR 全文一次性发送。

> **已验证：** 添加新模型只需新建 `ExamParseAiProperties`（`@ConfigurationProperties(prefix = "examparse.ai")`）+
> `application.yml` 配置段。复用现有 `ZhipuAiClient` Bean（`zai-sdk:0.3.3` 已在 pom），无需额外 SDK 依赖。

**适用条件：**
- 全文 token 估算 ≤ 100K（约 7–8 万汉字）
- 配图占位符仅为文本标记，不占用视觉上下文

**超出上限时的分块降级策略（P2）：**
1. 按 `=== PAGE N ===` 分块，每块 ≤ 30 页
2. 首先对全文做轻量预处理：让 LLM 识别题目边界索引（仅返回页码+题号，不返回正文），得到切割点
3. 再按切割点逐块精化拆分

MVP 阶段仅实现一次性拆分。

### 6.2 System Prompt（拆题 + 答案匹配）

```
你是一名专业的中国高中数学试卷解析助手。
你将收到一份试卷的 OCR 文本（可能来自多页 PDF 或多张图片），文本中的图片区域已被替换为占位符
<image ref="fig-{pageNo}-{seqNo}" bbox="x1,y1,x2,y2" globalPage="{pageNo}" />

你的任务：
1. 识别所有独立题目，忽略章节标题（如"一、选择题（共 60 分）"、"二、填空题"、"解答题"等），
   这些是结构性标题，不是题目本身。
2. 按照题目在试卷中出现的顺序为每题分配序号 seq（从 1 开始，连续递增）。
3. 如果试卷中包含答案部分（无论答案是紧跟在题目后、还是集中在试卷末尾），
   将答案与对应题目通过 seq 一一配对。对于"题目在前、答案在后"的格式，
   需根据题目编号（如 1. 2. 3. 或（1）（2）等）进行匹配。
4. 如有答案也有解析，请将"标准答案+解析过程"合并为完整答案文本一起输出。
5. 识别题目类型：SINGLE_CHOICE（单选）/ MULTI_CHOICE（多选）/ FILL_BLANK（填空）/ SHORT_ANSWER（解答）。
   无法确定时输出 UNKNOWN。
6. 对于题干中的图片占位符，原样保留其 ref 属性（如 fig-0-1），输出到 STEM_IMAGES 区块。
   对于答案中出现的图片占位符，输出到 ANSWER_IMAGES 区块。
7. 去除题目编号前缀（如 "1." "（1）" "第1题" "①"），只保留题目正文。
8. 保留 LaTeX 数学公式原样（如 $\frac{1}{2}$、$$\int_0^1 f(x)dx$$）。
9. 如果某道题目跨多页，请将跨页内容连续拼接，不要插入页码说明。

--- 输出格式 ---
严格按照以下标记格式输出，不得包含任何 Markdown 代码块、注释、说明文字。

###EXAM_PARSE_START###
###QUESTION_START### seq=1
###TYPE### SINGLE_CHOICE
###SOURCE_PAGES### 0,1
###STEM_START###
（题干正文，含 <image ref="fig-0-1" /> 等占位符，去掉 bbox 和 globalPage 属性，只保留 ref）
###STEM_END###
###STEM_IMAGES### fig-0-1 | fig-0-2
###ANSWER_START###
（答案正文及解析，若无答案则此区块内容为空，但标记保留）
###ANSWER_END###
###ANSWER_IMAGES### fig-1-2
###QUESTION_END###
###QUESTION_START### seq=2
...
###QUESTION_END###
###EXAM_PARSE_END###

--- 重要约束 ---
- 每道题必须有且仅有一个 ###QUESTION_START### 到 ###QUESTION_END### 块。
- ###TYPE###、###SOURCE_PAGES###、###STEM_IMAGES###、###ANSWER_IMAGES### 的值均在单行内，
  多个 ref 之间用 " | " 分隔。
- 若题干无图片，###STEM_IMAGES### 行内容留空（一个空格），继续保留该行。
- 若无答案，###ANSWER_START### 到 ###ANSWER_END### 之间留一行空（不可省略标记）。
- 决不允许改变题目顺序（seq 必须严格递增，与原试卷顺序一致）。
- LaTeX 公式必须完整保留，不得截断或转义。
- 严格避免将章节标题（如"选择题"、"填空题"）作为独立题目输出。
```

### 6.3 User Prompt 构造

```java
String hint = hasAnswerHint
    ? "\n[用户提示：此试卷包含答案，请确保将答案与题目一一匹配。]\n"
    : "";

String userPrompt = hint
    + "\n以下是试卷的 OCR 文本，请按照系统指令格式进行解析：\n\n"
    + aggregatedOcrText;
```

**Token 预算控制：**
- System Prompt 约 800 tokens
- OCR 全文最大 90,000 tokens（保留 ~38K 给输出）
- 超出时对 OCR 文本按页从末尾截断，记录 WARNING 日志

### 6.4 输出解析器（`ExamParseOutputParser`）

```
输入：LLM raw response (String)

步骤：
1. 查找 ###EXAM_PARSE_START### 和 ###EXAM_PARSE_END### 之间的内容
   （健壮：允许两端有多余空白）
2. 按 ###QUESTION_START### seq=N 分割，遍历每题块
3. 从每题块中提取：
   - TYPE（单行）
   - SOURCE_PAGES（单行，逗号分割为 List<Integer>）
   - STEM_START...STEM_END 之间的文本（题干正文）
   - STEM_IMAGES（单行，" | " 分割为 List<String>）
   - ANSWER_START...ANSWER_END 之间的文本（可为空）
   - ANSWER_IMAGES（单行，" | " 分割）
4. 构造 ParsedQuestion 对象列表，按 seq 升序排序
5. 对解析失败的题目（缺少必要标记）：
   - 记录 parseError=true，保留 rawText，继续处理其它题目
6. 返回 List<ParsedQuestion>

容错机制：
- ###EXAM_PARSE_START/END### 缺失：直接按 ###QUESTION_START### 分割
- ###TYPE### 缺失：默认 UNKNOWN
- ###STEM_IMAGES### 缺失：正则扫描 STEM 文本中 <image ref="..." 提取
```

---

## 7. 图片裁剪与关联机制

### 7.1 全局图片 Registry

OCR 整合阶段建立任务级别的全局图片 Registry（内存 `HashMap<String, ImageRegistryEntry>`），键为 `fig-{globalPage}-{figSeq}`：

```json
{
  "ref": "fig-0-1",
  "globalPage": 0,
  "bbox": "100,200,300,400",
  "sourceFileIndex": 0,
  "pageWithinFile": 0
}
```

执行裁剪时，根据 `sourceFileIndex` 和 `pageWithinFile` 定位到原始图片（PDF 转出的 PNG 页或直接上传的图片），调用现有 `ImageRegionCropper.crop()` 完成裁剪。

> **已验证：** `ImageRegionCropper.crop(String imageBase64, List<BboxRegion> regions)` 接收单张 base64 + 多个裁剪区域，与现有业务完全解耦。对多页场景，调用层按页循环调用即可。

### 7.2 逐题图片处理流程

```
ParsedQuestion.stemImageRefs = ["fig-0-1", "fig-1-2"]

for each ref in stemImageRefs (index i = 1, 2, ...):
  registry.get(ref) → ImageRegistryEntry(globalPage, bbox, sourceFileIndex, pageWithinFile)
  → crop(sourcePageImage, bbox) → ExtractedImage(base64 PNG)
  → 分配最终 ref: "img-{i}"
  → 替换 rawStemText 中的 <image ref="fig-0-1"/> 为 <image ref="img-1"/>

ParsedQuestion.answerImageRefs = ["fig-1-2"]

for each ref in answerImageRefs (index j = 1, 2, ...):
  → registry.get(ref) → crop → ExtractedImage
  → 分配最终 ref: "a{seqNo}-img-{j}"
  → 替换 rawAnswerText 中的 <image ref="fig-1-2"/> 为 <image ref="a1-img-1"/>
```

### 7.3 图片引用全链路映射汇总

| 阶段 | ref 格式 | 存储位置 |
|------|----------|----------|
| OCR 整合阶段（内部中间态） | `fig-{globalPage}-{figSeq}` | 全局 Registry（内存） |
| LLM 输出 → 题干 | `fig-{gP}-{fS}` → 裁剪后 `img-{i}` | `q_exam_parse_question.stem_images_json` |
| LLM 输出 → 答案 | `fig-{gP}-{fS}` → 裁剪后 `a{seqNo}-img-{j}` | `q_exam_parse_question.answer_images_json` |
| 用户确认后 → 题干资产 | `img-{i}` | `q_question_asset`（type=INLINE_IMAGE） |
| 用户确认后 → 答案资产 | `a{seqNo}-img-{j}` | `q_answer_asset` |

与现有单题 OCR 的命名规范完全对齐（`fig-N` → 题干，`a{uuid8}-img-N` → 答案），前端无需适配。

---

## 8. StemXmlConverter / AnswerXmlConverter 适配

拆题完成后，`ParsedQuestion.rawStemText` 已为干净单题文本（图片占位符已完成 ref 替换），**可直接调用现有 Converter，无需修改其 Prompt 和接口**。

额外适配事项：
- Converter 调用时传入 `seqNo` 参数用于日志追踪和错误定位
- 若某题 Converter 调用失败：
  - 写 `error_msg` 到 `q_exam_parse_question`
  - 降级：将 `rawStemText` 包裹为 `<stem version="1"><p>...</p></stem>`
  - 继续处理后续题目（任务状态标记为 `PARTIAL_FAILED`，不中断整个流程）

---

## 9. 题目顺序保证机制

| 层次 | 机制 |
|------|------|
| 文件层 | `q_exam_parse_source_file.file_index` 决定顺序；`globalPage` 按序递增 |
| OCR 整合层 | 按 `globalPage` 升序拼接 OCR 文本，页分隔符明确标注页码 |
| LLM 层 | System Prompt 明确要求 seq 严格按原试卷顺序递增，不得重排 |
| 解析层 | `ExamParseOutputParser` 按 seq 排序 `ParsedQuestion` 列表 |
| 存储层 | `q_exam_parse_question.seq_no` 存储顺序；查询时 `ORDER BY seq_no ASC` |

---

## 10. `ocr-service` 新增组件清单

| 组件 | 类名（建议） | 职责 |
|------|-------------|------|
| MQ Consumer | `ExamParseTaskConsumer` | 消费 `exam.parse.task.created`，驱动整个解析流程 |
| PDF 转图 | `PdfPageRenderer` | PDFBox 封装，PDF base64 → `List<PageImage>` |
| OCR 预处理 | `ExamPagePreprocessor` | **新建类**（不改现有 `OcrTextPreprocessor`），复用 `BBOX_PATTERN`，生成 `fig-{globalPage}-{figSeq}` 格式 ref |
| OCR 整合 | `MultiPageOcrAggregator` | 逐页调用 GLM-OCR + `ExamPagePreprocessor`，整合为全局文档；维护全局图片 Registry |
| LLM 拆题 | `ExamSplitLlmClient` | 调用 GLM-Z-Plus，System+User Prompt 封装，重试策略 |
| 输出解析 | `ExamParseOutputParser` | 按标记分割 LLM 输出 → `List<ParsedQuestion>` |
| 图片裁剪适配 | `ExamImageCropper` | 基于全局 Registry 批量裁剪，调用现有 `ImageRegionCropper` |
| XML 生成 | `ExamQuestionXmlGenerator` | 逐题调用 `StemXmlConverter` + `AnswerXmlConverter` |
| 结果发布 | `ExamParseResultPublisher` | MQ 发布 `exam.parse.result`（逐题） |
| 配置类 | `ExamParseAiProperties` | `@ConfigurationProperties(prefix = "examparse.ai")`，配置 model / temperature / max-tokens |

---

## 11. `question-service` 新增组件清单

| 组件 | 类名（建议） | 职责 |
|------|-------------|------|
| REST 控制器 | `ExamParseController` | 处理 `/api/exam-parse/*` 所有端点（放入 `...question.controller` 包，与 `QuestionController` 并列） |
| 任务提交服务 | `ExamParseCommandService` | 文件校验、入库、任务创建、MQ 发布 |
| 结果接收 Consumer | `ExamParseResultConsumer` | 消费 `exam.parse.result`；写 `q_exam_parse_question`；WS 推送 |
| 确认服务 | `ExamParseConfirmService` | `POST confirm`：逐题创建 `q_question`、`q_answer`、`q_question_asset`、`q_answer_asset` |
| Repository | `ExamParseTaskRepository` `ExamParseSourceFileRepository` `ExamParseQuestionRepository` | MyBatis-Plus CRUD |

---

## 12. RabbitMQ 拓扑扩展

> **已验证：** 现有 `RabbitTopologyConfig` 使用 `@Bean` 声明 Exchange/Queue/Binding + `public static final String` 常量，扩展新拓扑仅需仿照添加。

在 ocr-service 和 question-service 的 `RabbitTopologyConfig` 中新增：

| Exchange | Type | Routing Key | 生产者 | 消费者 |
|----------|------|-------------|--------|--------|
| `qforge.exam` | Topic | `exam.parse.task.created` | question-service | ocr-service `ExamParseTaskConsumer` |
| `qforge.exam` | Topic | `exam.parse.result` | ocr-service | question-service `ExamParseResultConsumer` |

**MQ 消息体 — `ExamParseTaskCreatedEvent`（`common-contract` 新增）：**

```json
{
  "taskUuid": "...",
  "ownerUser": "admin",
  "hasAnswerHint": true,
  "files": [
    { "fileIndex": 0, "fileName": "exam.pdf", "fileType": "PDF", "pageCount": 3, "fileDataBase64": "..." },
    { "fileIndex": 1, "fileName": "photo.jpg", "fileType": "IMAGE", "pageCount": 1, "fileDataBase64": "..." }
  ]
}
```

**MQ 消息体 — `ExamParseQuestionResultEvent`（`common-contract` 新增，逐题推送）：**

```json
{
  "taskUuid": "...",
  "seqNo": 1,
  "questionType": "SINGLE_CHOICE",
  "rawStemText": "...",
  "stemXml": "<stem version=\"1\">...</stem>",
  "rawAnswerText": "...",
  "answerXml": "<answer version=\"1\">...</answer>",
  "stemImages": [
    { "ref": "img-1", "imageData": "base64...", "mimeType": "image/png" }
  ],
  "answerImages": [
    { "ref": "a1-img-1", "imageData": "base64...", "mimeType": "image/png" }
  ],
  "sourcePages": [0, 1],
  "parseError": false,
  "errorMsg": null
}
```

**MQ 消息体 — `ExamParseCompletedEvent`（`common-contract` 新增，整卷完成通知）：**

```json
{
  "taskUuid": "...",
  "status": "SUCCESS",
  "questionCount": 20,
  "errorMsg": null
}
```

---

## 13. `common-contract` 新增事件类

> **已验证：** 现有 `common-contract` 中已有 `OcrTaskCreatedEvent`、`OcrTaskResultEvent`、`AiAnalysisTaskCreatedEvent`、`AiAnalysisResultEvent`、`DbWriteBackEvent`、`ExtractedImage` 等 record 类。新增事件直接仿照即可。

| 类名 | 说明 |
|------|------|
| `ExamParseTaskCreatedEvent` | 试卷解析任务创建事件（含文件 base64） |
| `ExamParseQuestionResultEvent` | 单题解析结果事件（逐题推送） |
| `ExamParseCompletedEvent` | 整卷解析完成事件（成功/失败汇总） |

`DbWriteBackEvent.taskType` 扩展新增值 `EXAM_PARSE` / `EXAM_PARSE_LOCAL`。

---

## 14. 前端交互设计

### 14.1 上传界面

- 文件选择：支持 `.pdf` / `.jpg` / `.jpeg` / `.png` / `.webp` 多选（最多 10 个文件）
- 文件排序：拖拽调整顺序（顺序决定全局页码和答案匹配方向）
- 可选勾选：「此试卷包含答案」（对应 `hasAnswerHint` 参数）
- 上传后立即显示进度条，通过 WebSocket 实时更新

### 14.2 解析结果预览

- 逐题展示（复用现有 `StemXml` 渲染模块）：解析完一题推送一题
- 每题状态：✅ 解析成功 / ⚠️ 解析异常（展示原始文本兜底）
- 每题操作：编辑题干 XML、编辑答案、手动替换图片
- 最终操作：「全部确认入库」或逐题「确认」/「跳过」

### 14.3 复用现有运行时模块

| 模块 | 文件 | 复用方式 |
|------|------|----------|
| `StemXml` | `frontend/src/stem-xml.js` | 直接用于渲染和验证每题 stemXml |
| `QForgeImageRuntime` | `frontend/src/runtime/image-runtime.js` | 管理题干图片 base64 引用 |
| `QForgeAnswerRuntime` | `frontend/src/runtime/answer-runtime.js` | 准备答案提交 payload |

---

## 15. 前置配置变更清单

实现前需要先完成以下基础设施配置：

### 15.1 gateway-service `application.yml`

```yaml
# 新增路由
spring:
  cloud:
    gateway:
      routes:
        - id: exam-parse-route
          uri: lb://question-service
          predicates:
            - Path=/api/exam-parse/**
  # 调高 WebFlux codec 内存限制以支持大文件透传
  codec:
    max-in-memory-size: 100MB
```

### 15.2 question-service `application.yml`

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 100MB
```

### 15.3 ocr-service `pom.xml`

```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.2</version>
</dependency>
```

### 15.4 ocr-service `application.yml`

```yaml
examparse:
  ai:
    model: glm-z-plus
    temperature: 0.1
    max-tokens: 32768
```

---

## 16. 可行性评估

对设计方案中涉及的 10 项关键技术点进行了逐一代码级验证：

| # | 验证项 | 结论 | 详细说明 |
|---|--------|------|----------|
| 1 | PDFBox 3.x 兼容性 | ✅ 通过 | Java 17 + Spring Boot 3.5，PDFBox 3.0.2 完全兼容。纯 Java 实现，无外部安装需求 |
| 2 | GLM-Z-Plus 模型集成 | ✅ 通过 | 现有 `ZhipuAiClient` Bean（`zai-sdk:0.3.3`）+ `@ConfigurationProperties` 模式清晰，新增 `ExamParseAiProperties` 即可 |
| 3 | Multipart 文件上传 | ⚠️ 需配置 | gateway-service（WebFlux）需添加 `spring.codec.max-in-memory-size`；question-service 需配置 `spring.servlet.multipart.*`；需添加 `/api/exam-parse/**` 路由 |
| 4 | RabbitMQ 拓扑扩展 | ✅ 通过 | 三个服务的 `RabbitTopologyConfig` 使用 `@Bean` 声明拓扑的统一模式，添加 `qforge.exam` Exchange + Queue 只需仿照现有代码 |
| 5 | `ImageRegionCropper` 复用 | ✅ 通过 | `crop(imageBase64, regions)` 接口与业务完全解耦，多页场景按页循环调用即可 |
| 6 | OCR 文本预处理 | ⚠️ 需适配 | 现有 `OcrTextPreprocessor` 生成 `fig-N` 格式 ref；**不修改它**，为 exam-parse 新建 `ExamPagePreprocessor`，复用 `BBOX_PATTERN` 常量生成 `fig-{globalPage}-{figSeq}` 格式 |
| 7 | `common-contract` 扩展 | ✅ 通过 | 新增 3 个 event record 类，仿照已有 `OcrTaskCreatedEvent` 等；`DbWriteBackEvent.taskType` 扩展新枚举值 |
| 8 | DB Schema 兼容 | ✅ 通过 | 前缀 `q_exam_parse_*` 与现有 12 张表无命名冲突；使用 `CREATE TABLE IF NOT EXISTS` |
| 9 | Controller 放置 | ✅ 通过 | `ExamParseController` 放入 `...question.controller` 包，`@RequestMapping("/api/exam-parse")` 完全匹配现有 `QuestionController` / `TagController` 模式 |
| 10 | WebSocket 推送 | ✅ 通过 | `OcrWsPushService.push(user, event, payload)` 中 event 为任意字符串、payload 为 `Map<String, Object>`，推送新事件零改动 |

**整体评估：方案技术可行，主要工作为新增代码，不改动现有业务逻辑，风险低。**

---

## 17. 关键风险与缓解措施

| 风险 | 严重度 | 缓解 |
|------|--------|------|
| OCR 文本超出 LLM 上下文长度 | 中 | Token 预算检查 + 末尾按页截断 + WARNING 日志；P2 实现分块降级 |
| LLM 拆题失序（seq 不连续或重复） | 低 | 解析器按 seq 排序 + 去重；检测 gap 并补充空题目（标记 parseError） |
| 图片 ref 在 LLM 输出中被改写 | 低 | Prompt 明确禁止；Parser 从 STEM/ANSWER 文本中正则扫描 `<image ref=` 作为兜底 |
| 答案-题目匹配错误（集中型答案） | 中 | Prompt 要求按题号匹配；前端展示供用户人工校对 |
| PDF 转图失败（加密/损坏） | 低 | 上传时 PDFBox 前置校验（尝试打开），失败立即返回 400 |
| MQ 消息体过大（含多文件 base64） | 中 | 源文件 base64 存入 DB 后，MQ 仅传 taskUuid + fileIndex 列表；Consumer 从 DB 拉取 |
| 大量裁图导致 MySQL 行宽超限 | 低 | 每张图片 ≤ 30 KB，`stem_images_json` 使用 TEXT 类型；超大图先压缩 |
| 超长解析任务占用 Consumer 线程 | 中 | Consumer 内部使用线程池；设置任务超时（最大 10 分钟），超时标记 FAILED |

---

## 18. 实现优先级

| 优先级 | 内容 |
|--------|------|
| **P0** | 单 PDF（多页）上传 → OCR → LLM 拆题 → 创建草稿题目（仅题干，无答案） |
| **P0** | 全局页码系统 + 图片裁剪 + ref 替换 |
| **P0** | 前端进度 WS 推送 + 逐题预览 + 批量确认入库 |
| **P1** | 含答案 PDF 解析（答案-题目自动匹配） |
| **P1** | 多图片上传（无 PDF） |
| **P1** | 混合输入（PDF + 图片组合） |
| **P2** | 分块降级策略（超长 OCR 文本） |
| **P2** | 前端逐题手动编辑与图片替换 |
| **P3** | 解析任务进度持久化（服务重启后恢复） |
