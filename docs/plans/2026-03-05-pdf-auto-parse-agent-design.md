# PDF 自动解析录入 Agent 设计文档

**日期:** 2026-03-05  
**状态:** 设计稿  
**关联:**
- [OCR 题库 MVP 设计文档](2026-02-28-ocr-question-bank-mvp-design.md)
- [XML 题干存储方案设计](2026-03-02-xml-stem-storage-design.md)
- [当前数据库 Schema](../current-database-schema.md)

---

## 1. 设计目标

### 1.1 核心场景

用户上传包含多道题目的 PDF 文件（如试卷、习题册扫描件），系统自动完成：

1. **PDF 页面转图片** — 将每页 PDF 渲染为高分辨率图片
2. **版面 OCR 识别** — 对每页图片调用 GLM-OCR (`layout_parsing`) 获取结构化文本
3. **AI 题目拆分** — 用 GLM-5 大模型从全文中识别并拆分出每道独立题目（含题干、选项、答案）
4. **图片区域提取** — 从 PDF 页面中裁剪出题目配图和选项配图
5. **Stem XML 生成** — 将每道题按 `<stem version="1">` schema 生成 XML
6. **批量创建草稿** — 自动在用户题库中创建 `DRAFT` 状态的题目记录
7. **用户确认** — 用户在前端逐题审阅、修正后确认

### 1.2 设计约束

| 约束 | 说明 |
|------|------|
| 每题至多一张题干配图 | 与现有 XML schema 一致，`<image ref>` 在 `<stem>` 下至多 1 个 |
| 选项可含图片 | `<choice>` 内可放 `<image ref />` |
| 答案可选 | PDF 中有答案则提取，无答案则仅创建题干 |
| PDF 页数上限 | MVP 限制单次上传 ≤ 50 页（防止资源滥用） |
| 文件大小上限 | ≤ 50MB |
| 支持格式 | `.pdf`（扫描件和电子排版件均支持） |

### 1.3 非目标（本期不做）

- Word/图片批量导入（仅支持 PDF）
- 多 PDF 合并解析
- 实时协同编辑解析结果
- 题目去重检测

---

## 2. 整体架构

### 2.1 数据流总览

```
┌─────────────┐     multipart/form-data      ┌───────────────────┐
│  Electron    │  ─────────────────────────►  │  gateway-service  │
│  Frontend    │                              │  (:8080)          │
└─────┬───────┘                               └────────┬──────────┘
      │ WebSocket                                      │ route
      │ (进度推送)                                      ▼
      │                                       ┌───────────────────┐
      │◄────────── WS push ──────────────────│ question-service  │
      │                                       │ (:8089)           │
      │                                       └────────┬──────────┘
      │                                                │ MQ: pdf.parse.task.created
      │                                                ▼
      │                                       ┌───────────────────┐
      │                                       │  ocr-service      │
      │                                       │  (:8090)          │
      │                                       │                   │
      │                                       │  ┌─ PDFBox 转图 ─┐│
      │                                       │  │  页面 → 图片   ││
      │                                       │  └───────┬───────┘│
      │                                       │          ▼        │
      │                                       │  ┌─ GLM-OCR ────┐│
      │                                       │  │  图片 → 文本   ││
      │                                       │  └───────┬───────┘│
      │                                       │          ▼        │
      │                                       │  ┌─ GLM-5 ──────┐│
      │                                       │  │  文本 → 拆分  ││
      │                                       │  │  → stem XML   ││
      │                                       │  │  → 答案提取   ││
      │                                       │  │  → 图片区域   ││
      │                                       │  └───────┬───────┘│
      │                                       │          │        │
      │                                       └──────────┼────────┘
      │                                                  │ MQ: pdf.parse.result
      │                                                  ▼
      │                                       ┌───────────────────┐
      │◄────────── WS push ─────────────────│ question-service  │
      │  (逐题推送解析结果)                    │ (创建草稿题目)     │
                                              └───────────────────┘
```

### 2.2 服务职责分工

| 服务 | 职责 |
|------|------|
| **Frontend (Electron)** | PDF 文件选择、上传、进度展示、解析结果预览/编辑/确认 |
| **gateway-service** | JWT 鉴权、路由、文件大小限制 |
| **question-service** | 创建解析任务、接收解析结果、批量创建草稿题目、WebSocket 推送 |
| **ocr-service** | PDF→图片、OCR、AI 拆分、stem XML 生成、图片裁剪 |

---

## 3. 核心业务流程

### 3.1 完整时序

```
用户                Frontend              question-service          RabbitMQ              ocr-service
 │                    │                        │                      │                      │
 │  选择 PDF 文件     │                        │                      │                      │
 │──────────────────►│                        │                      │                      │
 │                    │  POST /api/pdf-tasks   │                      │                      │
 │                    │  (multipart: file)     │                      │                      │
 │                    │───────────────────────►│                      │                      │
 │                    │                        │  创建 q_pdf_parse_task│                      │
 │                    │                        │  status=PENDING       │                      │
 │                    │  202 Accepted          │                      │                      │
 │                    │  { taskUuid }          │                      │                      │
 │                    │◄───────────────────────│                      │                      │
 │                    │                        │  publish             │                      │
 │                    │                        │  pdf.parse.created   │                      │
 │                    │                        │─────────────────────►│                      │
 │                    │                        │                      │  consume             │
 │                    │                        │                      │─────────────────────►│
 │                    │                        │                      │                      │
 │                    │                        │                      │  1. PDF → 图片(PDFBox)│
 │                    │                        │                      │  2. 逐页 OCR         │
 │                    │     WS: progress       │    progress event    │  3. AI 拆分题目      │
 │                    │◄──────────────────────│◄─────────────────────│◄─────────────────────│
 │  显示进度 30%      │                        │                      │  4. 逐题生成 XML     │
 │◄───────────────── │                        │                      │  5. 裁剪配图         │
 │                    │                        │                      │  6. 提取答案         │
 │                    │     WS: progress       │    progress event    │                      │
 │                    │◄──────────────────────│◄─────────────────────│◄─────────────────────│
 │  显示进度 80%      │                        │                      │                      │
 │◄───────────────── │                        │                      │                      │
 │                    │                        │                      │  publish 最终结果     │
 │                    │                        │  pdf.parse.result    │                      │
 │                    │                        │◄─────────────────────│◄─────────────────────│
 │                    │                        │                      │                      │
 │                    │                        │  批量创建 DRAFT 题目  │                      │
 │                    │                        │  写入 q_question      │                      │
 │                    │                        │  写入 q_question_asset│                      │
 │                    │                        │  写入 q_answer        │                      │
 │                    │                        │                      │                      │
 │                    │  WS: task.completed    │                      │                      │
 │                    │  {questions: [...]}    │                      │                      │
 │                    │◄──────────────────────│                      │                      │
 │  显示解析结果列表   │                        │                      │                      │
 │◄───────────────── │                        │                      │                      │
 │                    │                        │                      │                      │
 │  逐题确认/修正     │  PATCH /api/questions  │                      │                      │
 │──────────────────►│───────────────────────►│                      │                      │
```

### 3.2 阶段说明

#### Phase 1: PDF 上传与任务创建

1. 前端通过 `POST /api/pdf-tasks` 上传 PDF 文件（multipart/form-data）
2. `question-service` 校验文件格式与大小，创建 `q_pdf_parse_task` 记录（`PENDING`）
3. 将 PDF 二进制内容编码为 base64，发布 `PdfParseTaskCreatedEvent` 到 MQ
4. 返回 `202 Accepted` + `taskUuid`

#### Phase 2: PDF 解析（ocr-service）

1. **PDF → 图片**：使用 Apache PDFBox 将每页渲染为 300 DPI 的 PNG 图片
2. **逐页 OCR**：对每页图片调用 GLM-OCR (`layout_parsing`)，获取结构化文本和版面坐标
3. **全文拼接**：将所有页面 OCR 结果按页序拼接，保留页面分隔标记
4. **AI 题目拆分**：调用 GLM-5，以 system prompt 指导其识别题目边界、拆分为独立题目
5. **逐题处理**：
   - 生成 `<stem version="1">` XML
   - 识别并裁剪题干配图（至多 1 张）
   - 识别并裁剪选项配图
   - 提取答案（如有）
6. **进度上报**：每完成一个阶段通过 MQ 发布进度事件

#### Phase 3: 结果写入（question-service）

1. 消费 `PdfParseResultEvent`，在一个事务中：
   - 为每道题创建 `q_question` 记录（`DRAFT`）
   - 存储题干/选项配图到 `q_question_asset`
   - 若有答案，创建 `q_answer` 记录
   - 关联到 `q_pdf_parse_task`
2. 通过 WebSocket 推送完整解析结果，前端展示题目列表

#### Phase 4: 用户确认

1. 前端展示 XML 渲染的题目预览（复用现有 XML 渲染器）
2. 用户可修正题干、答案、标签，然后逐题确认
3. 确认后触发 `DRAFT → READY` 状态流转

---

## 4. AI Prompt 设计

### 4.1 题目拆分 Prompt（核心）

```
SYSTEM:
你是一个专业的试卷分析引擎。输入是从 PDF 试卷中 OCR 识别的全文文本，你需要完成以下任务：

## 任务
1. 识别并拆分出每一道独立的题目
2. 对每道题目，识别其结构（题干、选项、答案、配图区域）
3. 判断题型并输出结构化 JSON

## 输入格式
文本中 `===PAGE {n}===` 标记页面分隔。
文本中 `[IMAGE: page={p}, bbox={x1,y1,x2,y2}]` 标记 OCR 检测到的图片区域。

## 题目边界识别规则
- 题号模式：阿拉伯数字+点（如 "1." "2."）、圆括号括题号（如 "(1)" "(2)"）
- 大题标号：一、二、三、四...（大题下可能有小题）
- "选择题"/"填空题"/"解答题" 等分类标题本身不是题目
- 答案区域（如"参考答案"标题后的内容）需与对应题目关联

## 输出格式
严格输出以下 JSON 数组，不添加任何额外文字：
```json
{
  "questions": [
    {
      "seq": 1,
      "type": "single_choice | multi_choice | fill_blank | essay",
      "stem_text": "题干纯文本（含 LaTeX）",
      "stem_image": { "page": 1, "bbox": [x1, y1, x2, y2] } | null,
      "choices": [
        { "key": "A", "text": "选项文本", "image": { "page": 1, "bbox": [...] } | null },
        ...
      ] | null,
      "answer": "答案文本（LaTeX 格式）" | null,
      "source_pages": [1, 2]
    }
  ]
}
```

## 关键规则
- `stem_image`：仅在题干确实包含示意图/图表时才提取，至多 1 张
- `choices[].image`：选项本身是图片时提取（如几何图形选项）
- `answer`：如果 PDF 含答案区域，关联到对应题目；否则为 null
- `source_pages`：标记该题跨越的页码，用于后续图片裁剪定位
- LaTeX 公式保持原样，使用 $...$ 包裹
- 去除题号前缀
```

### 4.2 两阶段策略

由于 PDF 可能包含大量文本（超出单次 token 限制），采用两阶段处理：

| 阶段 | 输入 | 输出 | 目的 |
|------|------|------|------|
| **Stage 1: 拆分** | 全文 OCR 文本 | 题目边界 JSON（题号、起止位置、类型、配图坐标） | 确定每题范围 |
| **Stage 2: 精化** | 单题文本 + 上下文 | 完整 stem XML + 答案 | 精准生成 XML |

**Stage 1** 使用 4.1 的 Prompt。如果全文超过 GLM-5 上下文窗口（128K tokens），按页分组处理，每组 ≤ 10 页。

**Stage 2** 复用现有 `StemXmlConverter`（已在 ocr-service 中实现），对每道题的 `stem_text` 调用一次，生成标准 `<stem version="1">` XML。

### 4.3 分页策略（长 PDF 处理）

```
PDF 总页数 ≤ 10:  一次性发送全部 OCR 文本到 Stage 1
PDF 总页数 > 10:  按 10 页一组分批处理 Stage 1
                  合并所有分批结果后进入 Stage 2
```

> 分批时需要处理跨页题目：每批保留前一批最后 2 页的内容作为上下文重叠，Stage 1 返回的题目按 `seq` 去重合并。

---

## 5. 数据库扩展

### 5.1 新增表：`q_pdf_parse_task`

```sql
CREATE TABLE IF NOT EXISTS q_pdf_parse_task (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_uuid       CHAR(36)      NOT NULL UNIQUE,
    owner_user      VARCHAR(128)  NOT NULL,
    file_name       VARCHAR(255)  NOT NULL   COMMENT '上传的 PDF 文件名',
    file_size       BIGINT        NOT NULL   COMMENT '文件大小（字节）',
    page_count      INT           NULL       COMMENT 'PDF 总页数（解析后回填）',
    status          VARCHAR(32)   NOT NULL   COMMENT 'PENDING / PROCESSING / SPLITTING / GENERATING / SUCCESS / FAILED',
    progress        INT           NOT NULL DEFAULT 0 COMMENT '进度百分比 0-100',
    question_count  INT           NULL       COMMENT '解析出的题目数量',
    error_msg       VARCHAR(1024) NULL,
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pdf_task_owner_status (owner_user, status, created_at)
) COMMENT 'PDF 自动解析任务';
```

### 5.2 新增表：`q_pdf_parse_question`

```sql
CREATE TABLE IF NOT EXISTS q_pdf_parse_question (
    id              BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id         BIGINT        NOT NULL,
    seq_no          INT           NOT NULL   COMMENT '在该 PDF 中的题目序号',
    question_id     BIGINT        NULL       COMMENT '关联的 q_question.id（创建草稿后回填）',
    raw_stem_text   LONGTEXT      NULL       COMMENT 'AI 拆分出的原始题干文本',
    stem_xml        LONGTEXT      NULL       COMMENT '生成的 stem XML',
    raw_answer      LONGTEXT      NULL       COMMENT '提取的原始答案文本',
    question_type   VARCHAR(32)   NULL       COMMENT 'SINGLE_CHOICE / MULTI_CHOICE / FILL_BLANK / ESSAY',
    source_pages    VARCHAR(255)  NULL       COMMENT '来源页码，逗号分隔',
    status          VARCHAR(32)   NOT NULL   COMMENT 'PARSED / DRAFT_CREATED / CONFIRMED / DISCARDED',
    created_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_pdf_q_task_seq (task_id, seq_no),
    CONSTRAINT fk_pdf_q_task FOREIGN KEY (task_id) REFERENCES q_pdf_parse_task(id)
) COMMENT 'PDF 解析出的题目中间结果';
```

### 5.3 现有表关联

```
q_pdf_parse_task  1───N  q_pdf_parse_question
                                │
                                │ question_id (nullable FK)
                                ▼
                         q_question ──── q_question_asset
                                   ──── q_answer
```

解析完成后，每个 `q_pdf_parse_question` 关联一个 `q_question`（DRAFT），用户确认后 `q_question` 流转为 READY。

### 5.4 PDF 文件存储策略

| 方案 | 优点 | 缺点 | 选择 |
|------|------|------|------|
| 数据库 LONGBLOB | 简单，事务一致 | 大文件占 DB 空间 | ❌ |
| 本地文件系统 | 简单快速 | 不利于容器化 | ❌ |
| **MQ 消息体传递 base64** | 无需持久存储 PDF，解析完即丢弃 | 消息体较大 | ✅ MVP |
| 对象存储（MinIO/S3） | 生产推荐 | 增加基础设施依赖 | 预留 |

**MVP 方案**：PDF 文件以 base64 编码放入 MQ 消息体，`ocr-service` 消费后内存中处理，不持久化 PDF 原文。`q_pdf_parse_task` 只记录元信息（文件名、大小）。

> 对于 50MB 以内的 PDF，base64 编码后约 67MB，RabbitMQ 默认最大消息大小为 128MB，可满足需求。生产环境建议使用对象存储 + 消息引用模式。

---

## 6. MQ 事件设计

### 6.1 Exchange 与 Queue

```
Exchange: qforge.pdf (topic)

Routing Keys:
  pdf.parse.task.created   → 新 PDF 解析任务
  pdf.parse.progress       → 进度更新
  pdf.parse.result         → 解析完成结果

Queues:
  qforge.pdf.parse.task.q       ← 绑定 pdf.parse.task.created
  qforge.pdf.parse.progress.q   ← 绑定 pdf.parse.progress
  qforge.pdf.parse.result.q     ← 绑定 pdf.parse.result
  qforge.pdf.parse.dlq          ← 死信队列
```

### 6.2 事件定义

#### `PdfParseTaskCreatedEvent`

```java
public record PdfParseTaskCreatedEvent(
    String taskUuid,
    String fileName,
    String pdfBase64,      // PDF 文件 base64 编码
    String requestUser,
    int maxPages           // 页数上限
) {}
```

#### `PdfParseProgressEvent`

```java
public record PdfParseProgressEvent(
    String taskUuid,
    String requestUser,
    String phase,          // CONVERTING / OCR / SPLITTING / GENERATING
    int progress,          // 0-100
    String message         // 人类可读进度描述
) {}
```

#### `PdfParseResultEvent`

```java
public record PdfParseResultEvent(
    String taskUuid,
    String requestUser,
    boolean success,
    String errorMessage,
    int totalQuestions,
    List<ParsedQuestion> questions
) {
    public record ParsedQuestion(
        int seq,
        String questionType,    // SINGLE_CHOICE / MULTI_CHOICE / FILL_BLANK / ESSAY
        String stemXml,         // 完整 stem XML
        String stemImageBase64, // 题干配图 base64（可为 null）
        String stemImageMime,   // image/png
        List<ChoiceImage> choiceImages,
        String answerText,      // LaTeX 格式答案（可为 null）
        String sourcePages      // "1,2"
    ) {}

    public record ChoiceImage(
        String choiceKey,       // A, B, C, D
        String imageBase64,
        String imageMime
    ) {}
}
```

### 6.3 WebSocket 推送事件

| 事件 | 触发条件 | payload 示例 |
|------|---------|-------------|
| `pdf.task.accepted` | 任务创建成功 | `{ taskUuid, fileName }` |
| `pdf.task.progress` | 解析进度更新 | `{ taskUuid, phase, progress, message }` |
| `pdf.task.completed` | 解析成功 | `{ taskUuid, questionCount, questions: [{questionUuid, stemXml, ...}] }` |
| `pdf.task.failed` | 解析失败 | `{ taskUuid, errorMessage }` |

---

## 7. REST API 设计

### 7.1 上传 PDF 并创建解析任务

```
POST /api/pdf-tasks
Content-Type: multipart/form-data

Form Fields:
  file: (binary) PDF 文件

Response: 202 Accepted
{
  "taskUuid": "a1b2c3d4-...",
  "fileName": "高三数学月考.pdf",
  "status": "PENDING",
  "createdAt": "2026-03-05T10:00:00Z"
}
```

### 7.2 查询解析任务状态

```
GET /api/pdf-tasks/{taskUuid}

Response: 200 OK
{
  "taskUuid": "a1b2c3d4-...",
  "fileName": "高三数学月考.pdf",
  "fileSize": 2048000,
  "pageCount": 8,
  "status": "SUCCESS",
  "progress": 100,
  "questionCount": 25,
  "questions": [
    {
      "seq": 1,
      "questionUuid": "cdf61c86-...",
      "questionType": "SINGLE_CHOICE",
      "stemXml": "<stem version=\"1\">...</stem>",
      "status": "DRAFT_CREATED"
    }
  ],
  "createdAt": "2026-03-05T10:00:00Z",
  "updatedAt": "2026-03-05T10:02:30Z"
}
```

### 7.3 查询用户的解析任务列表

```
GET /api/pdf-tasks?page=0&size=20

Response: 200 OK
{
  "content": [...],
  "totalElements": 5,
  "totalPages": 1
}
```

### 7.4 丢弃某道解析出的题目

```
DELETE /api/pdf-tasks/{taskUuid}/questions/{seq}

Response: 200 OK
```

### 7.5 批量确认并触发 AI 分析

```
POST /api/pdf-tasks/{taskUuid}/confirm

Response: 200 OK
{
  "confirmedCount": 23,
  "discardedCount": 2
}
```

> 确认后自动触发 AI 标签/难度分析（复用现有 `AiAnalysisTaskConsumer`）。

---

## 8. ocr-service 内部处理流程

### 8.1 PdfParseWorker 核心逻辑

```java
@Component
public class PdfParseTaskConsumer {

    @RabbitListener(queues = "qforge.pdf.parse.task.q")
    public void onPdfParseTask(PdfParseTaskCreatedEvent event) {
        try {
            // Phase 1: PDF → Images
            publishProgress(event, "CONVERTING", 5, "正在将 PDF 转换为图片...");
            List<PageImage> pageImages = pdfToImages(event.pdfBase64());

            // Phase 2: OCR per page
            publishProgress(event, "OCR", 15, "正在识别文字...");
            List<PageOcrResult> ocrResults = new ArrayList<>();
            for (int i = 0; i < pageImages.size(); i++) {
                PageOcrResult result = glmOcrClient.recognizeWithLayout(pageImages.get(i));
                ocrResults.add(result);
                int progress = 15 + (int)(50.0 * (i + 1) / pageImages.size());
                publishProgress(event, "OCR", progress,
                    String.format("OCR 识别中 (%d/%d)...", i + 1, pageImages.size()));
            }

            // Phase 3: AI Split
            publishProgress(event, "SPLITTING", 70, "正在拆分题目...");
            String fullText = buildFullText(ocrResults);
            List<RawQuestion> rawQuestions = aiSplitQuestions(fullText);

            // Phase 4: Generate stem XML + crop images
            publishProgress(event, "GENERATING", 80, "正在生成题目...");
            List<ParsedQuestion> results = new ArrayList<>();
            for (RawQuestion rq : rawQuestions) {
                String stemXml = stemXmlConverter.convertToStemXml(rq.stemText());
                byte[] stemImage = cropImage(pageImages, rq.stemImageRegion());
                List<ChoiceImage> choiceImages = cropChoiceImages(pageImages, rq.choices());
                results.add(new ParsedQuestion(rq.seq(), stemXml, stemImage, choiceImages, rq.answer()));
            }

            // Phase 5: Publish result
            publishResult(event, true, results);

        } catch (Exception ex) {
            publishResult(event, false, ex.getMessage());
        }
    }
}
```

### 8.2 PDF 转图片（PDFBox）

```java
private List<PageImage> pdfToImages(String pdfBase64) throws IOException {
    byte[] pdfBytes = Base64.getDecoder().decode(pdfBase64);
    try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
        PDFRenderer renderer = new PDFRenderer(doc);
        List<PageImage> images = new ArrayList<>();
        for (int i = 0; i < doc.getNumberOfPages(); i++) {
            BufferedImage bim = renderer.renderImageWithDPI(i, 300, ImageType.RGB);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(bim, "png", baos);
            images.add(new PageImage(i + 1, baos.toByteArray()));
        }
        return images;
    }
}
```

### 8.3 OCR 结果与版面坐标

GLM-OCR `layout_parsing` 返回的 JSON 中包含文本块及其 bounding box 坐标：

```json
{
  "content": {
    "pages": [
      {
        "page_num": 1,
        "blocks": [
          { "type": "text", "text": "1. 下列...", "bbox": [72, 100, 540, 150] },
          { "type": "image", "bbox": [100, 200, 400, 500] },
          { "type": "text", "text": "A. ...", "bbox": [72, 520, 540, 560] }
        ]
      }
    ]
  }
}
```

这些坐标用于：
1. 定位题目配图区域（`type: "image"` 的 bbox）
2. 从原始 PDF 页面图片中裁剪出精确的配图

### 8.4 图片裁剪

```java
private byte[] cropImage(List<PageImage> pageImages, ImageRegion region) {
    if (region == null) return null;
    BufferedImage fullPage = ImageIO.read(
        new ByteArrayInputStream(pageImages.get(region.page() - 1).data()));

    // bbox 坐标转换：OCR 坐标系 → 渲染图片像素坐标
    int x = (int)(region.bbox()[0] * dpiScale);
    int y = (int)(region.bbox()[1] * dpiScale);
    int w = (int)((region.bbox()[2] - region.bbox()[0]) * dpiScale);
    int h = (int)((region.bbox()[3] - region.bbox()[1]) * dpiScale);

    BufferedImage cropped = fullPage.getSubimage(x, y, w, h);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    ImageIO.write(cropped, "png", baos);
    return baos.toByteArray();
}
```

---

## 9. 前端设计

### 9.1 PDF 上传页面

```
┌─────────────────────────────────────────────┐
│  PDF 智能导入                                │
│                                             │
│  ┌─────────────────────────────────────┐    │
│  │                                     │    │
│  │     将 PDF 文件拖放到此处            │    │
│  │     或点击选择文件                    │    │
│  │                                     │    │
│  │     支持 .pdf 格式，最大 50MB        │    │
│  │                                     │    │
│  └─────────────────────────────────────┘    │
│                                             │
│  最近导入:                                   │
│  ┌───────────────────────────────────────┐  │
│  │ 高三数学月考.pdf    25题  ✅ 已完成    │  │
│  │ 物理期末卷.pdf      30题  🔄 解析中   │  │
│  └───────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

### 9.2 解析进度页面

```
┌─────────────────────────────────────────────┐
│  正在解析: 高三数学月考.pdf                   │
│                                             │
│  ████████████████░░░░░░  70%                │
│                                             │
│  ✅ PDF 转图片 (8页)                         │
│  ✅ OCR 文字识别 (8/8)                       │
│  🔄 AI 题目拆分...                           │
│  ○ 生成题目                                  │
│                                             │
│  预计剩余时间: 约 30 秒                       │
└─────────────────────────────────────────────┘
```

### 9.3 解析结果审阅页面

```
┌──────────────────────────────────────────────────────────────┐
│  解析结果: 高三数学月考.pdf  (共 25 题)                        │
│  [全部确认]  [全部丢弃]                                       │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ #1  单选题                              [确认] [丢弃]  │  │
│  │                                                        │  │
│  │ 下列哪个是勾股定理的正确表述？                           │  │
│  │ A. $a^2 + b^2 = c^2$                                  │  │
│  │ B. $a + b = c$                                         │  │
│  │ C. $a^2 - b^2 = c^2$                                  │  │
│  │ D. $a^2 + b^2 = c$                                    │  │
│  │                                                        │  │
│  │ 答案: A                                                │  │
│  │                                          [编辑 XML]    │  │
│  └────────────────────────────────────────────────────────┘  │
│                                                              │
│  ┌────────────────────────────────────────────────────────┐  │
│  │ #2  解答题（含配图）                     [确认] [丢弃]  │  │
│  │                                                        │  │
│  │ 如图所示，求阴影部分的面积。              ┌──────────┐  │  │
│  │                                          │ [配图]   │  │  │
│  │                                          └──────────┘  │  │
│  └────────────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────┘
```

### 9.4 前端实现要点

1. **PDF 上传**：使用 `<input type="file" accept=".pdf">` + `FormData` 发送
2. **进度展示**：监听 WebSocket `pdf.task.progress` 事件，更新进度条
3. **结果渲染**：复用现有 `renderStemXml()` 函数渲染 XML 题干
4. **编辑模式**：点击"编辑 XML"可直接修改 XML 源码，支持实时预览
5. **批量操作**：「全部确认」一键将所有解析题目标记为确认

---

## 10. 错误处理与容错

### 10.1 错误分类

| 错误类型 | 处理策略 | 用户提示 |
|---------|---------|---------|
| PDF 格式错误/加密 | 拒绝，返回 400 | "文件格式不支持或已加密" |
| PDF 超过页数限制 | 拒绝，返回 400 | "文件超过 50 页限制" |
| GLM-OCR 超时 | 重试 2 次后失败 | "OCR 识别超时，请重试" |
| GLM-5 拆分失败 | 重试 1 次后降级为整页 OCR | "部分题目可能需要手动拆分" |
| 图片裁剪失败 | 跳过配图，仅保留文字 | "部分配图未能提取，可手动添加" |
| MQ 消息丢失 | DLQ + 定时补偿扫描 | 自动重试 |

### 10.2 重试策略

```java
// RabbitMQ 消费重试配置
spring.rabbitmq.listener.simple.retry.enabled=true
spring.rabbitmq.listener.simple.retry.max-attempts=3
spring.rabbitmq.listener.simple.retry.initial-interval=5000
spring.rabbitmq.listener.simple.retry.multiplier=2
```

### 10.3 超时控制

| 阶段 | 超时时间 | 说明 |
|------|---------|------|
| PDF 上传 | 120s | gateway 限制 |
| 单页 OCR | 60s | GLM-OCR 默认超时 |
| AI 拆分 | 120s | GLM-5 大模型调用 |
| 单题 XML 生成 | 30s | StemXmlConverter |
| 整体任务 | 10min | 超过则标记为 FAILED |

---

## 11. 技术依赖

### 11.1 新增 Maven 依赖（ocr-service）

```xml
<!-- Apache PDFBox: PDF → Image -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.4</version>
</dependency>
```

> PDFBox 是纯 Java 实现，无需安装原生库，适合 Docker 部署。

### 11.2 网关文件上传配置（gateway-service）

```yaml
spring:
  cloud:
    gateway:
      routes:
        - id: pdf-upload
          uri: lb://question-service
          predicates:
            - Path=/api/pdf-tasks/**
          filters:
            - name: RequestSize
              args:
                maxSize: 50MB
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

### 11.3 RabbitMQ 消息大小配置

```yaml
# RabbitMQ 服务端配置（infra compose）
rabbitmq:
  environment:
    RABBITMQ_SERVER_ADDITIONAL_ERL_ARGS: "-rabbit max_message_size 134217728"
```

---

## 12. 性能与扩展性

### 12.1 性能预估

| 指标 | 预估值 | 说明 |
|------|--------|------|
| 单页 OCR 时间 | 3-8s | 取决于页面复杂度 |
| 10 页 PDF 总处理时间 | 60-120s | OCR + AI 拆分 + XML 生成 |
| 30 页 PDF 总处理时间 | 3-5min | 分批处理 |
| 单次内存峰值 | ~500MB | 300DPI PDF 渲染 |

### 12.2 可扩展方向

1. **水平扩展**：`ocr-service` 多实例部署，MQ 自动负载均衡
2. **并行 OCR**：多页同时调用 GLM-OCR（线程池控制并发数）
3. **对象存储**：PDF / 图片存 MinIO/S3，消息体只传引用
4. **缓存**：相同 PDF 指纹（SHA-256）去重，避免重复解析
5. **流式处理**：解析完一题即推送一题，无需等待全部完成

---

## 13. 安全考量

| 风险 | 措施 |
|------|------|
| 恶意 PDF（漏洞利用） | PDFBox 本身较安全；限制文件大小；Docker 隔离 |
| API key 泄露 | 环境变量注入，不硬编码 |
| 用户数据隔离 | 所有查询带 `owner_user` 过滤 |
| 资源耗尽 | 文件大小 + 页数限制 + 全局并发控制 |
| 注入攻击 | XML 生成使用 DOM builder 而非字符串拼接 |

---

## 14. 交付标准（Definition of Done）

### P0（核心闭环）

- [ ] 前端可上传 PDF 文件
- [ ] 后端创建解析任务并返回 taskUuid
- [ ] PDF 按页转为图片并逐页 OCR
- [ ] AI 正确拆分出独立题目（准确率 > 80%）
- [ ] 每题生成合法 stem XML（StemXmlValidator 校验通过）
- [ ] 题干配图和选项配图正确裁剪并存入 `q_question_asset`
- [ ] 答案正确提取并存入 `q_answer`
- [ ] WebSocket 实时推送解析进度
- [ ] 用户可审阅并确认/丢弃解析出的题目
- [ ] 确认后 DRAFT → READY 状态正确流转

### P1（体验优化）

- [ ] 长 PDF 分批处理（> 10 页）
- [ ] 解析失败自动重试
- [ ] 前端解析结果支持编辑 XML 源码
- [ ] 批量确认/批量丢弃

### P2（后续迭代）

- [ ] PDF 指纹去重
- [ ] 解析历史记录管理
- [ ] 对象存储支持
- [ ] 流式逐题推送

---

## 15. 实现优先级与里程碑

| 阶段 | 内容 | 预估工时 |
|------|------|---------|
| **M1: 基础设施** | 新建表、MQ topology、API 骨架 | 1 天 |
| **M2: PDF → OCR** | PDFBox 集成、逐页 OCR、GLM-OCR 适配 | 1.5 天 |
| **M3: AI 拆分** | 拆分 Prompt 调试、JSON 解析、stem XML 生成 | 2 天 |
| **M4: 图片裁剪** | bbox 坐标转换、图片裁剪、asset 存储 | 1 天 |
| **M5: 前端** | 上传页、进度页、审阅页、WebSocket 集成 | 2 天 |
| **M6: 联调测试** | 端到端测试、Prompt 调优、错误处理 | 1.5 天 |

**总计：约 9 个工作日**
