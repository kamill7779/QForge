# Question Service — API 接口文档

> **版本**: 2026-03-02 · **Base URL**: `/api` · **认证**: 所有接口通过 `X-Auth-User` 请求头传递当前用户标识

---

## 目录

1. [通用约定](#通用约定)
2. [题目管理接口](#题目管理接口)
   - [获取题目列表](#get-apiquestions)
   - [创建草稿](#post-apiquestions)
   - [更新题干](#put-apiquestionsquestionuuidstem)
   - [添加答案](#post-apiquestionsquestionuuidanswers)
   - [编辑答案](#put-apiquestionsquestionuuidanswersansweruuid)
   - [删除答案](#delete-apiquestionsquestionuuidanswersansweruuid)
   - [完成题目](#post-apiquestionsquestionuuidcomplete)
   - [删除草稿](#delete-apiquestionsquestionuuid)
3. [OCR 任务接口](#ocr-任务接口)
   - [提交 OCR 任务](#post-apiquestionsquestionuuidocr-tasks)
4. [标签接口](#标签接口)
   - [获取标签目录](#get-apitags)
5. [WebSocket 推送](#websocket-推送)
6. [数据结构参考](#数据结构参考)
7. [错误响应格式](#错误响应格式)
8. [业务错误码一览](#业务错误码一览)

---

## 通用约定

| 项目 | 说明 |
|------|------|
| **请求头** | `X-Auth-User: <string>` — 当前操作用户（由 gateway 注入，前端无需手动设置；缺省为 `anonymous`） |
| **Content-Type** | `application/json` |
| **UUID** | 题目使用 `questionUuid`，答案使用 `answerUuid`，OCR 任务使用 `taskUuid`，均为 36 位 UUID 字符串 |
| **题目状态** | `DRAFT`（草稿）→ `READY`（完成） |
| **逻辑删除** | 答案的 `DELETE` 操作为软删除（`deleted=1`），不影响已有数据统计 |

---

## 题目管理接口

### `GET /api/questions`

获取当前用户的所有题目列表（含答案摘要、标签快照）。

**Response** `200 OK`

```json
[
  {
    "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
    "status": "DRAFT",
    "stemText": "<stem><p>求解方程 <equation>x^2 + 1 = 0</equation></p></stem>",
    "mainTags": [
      {
        "categoryCode": "SUBJECT",
        "categoryName": "学科",
        "tagCode": "MATH",
        "tagName": "数学"
      }
    ],
    "secondaryTags": ["高考", "选择题"],
    "answerCount": 2,
    "answers": [
      {
        "answerUuid": "660e8400-e29b-41d4-a716-446655440001",
        "answerType": "LATEX_TEXT",
        "latexText": "x = \\pm i",
        "sortOrder": 1,
        "official": false
      }
    ],
    "updatedAt": "2026-03-02T14:30:00"
  }
]
```

---

### `POST /api/questions`

创建一个空白草稿题目。请求体可选——可传入初始题干文本，也可留空后续通过 `PUT /stem` 设置。

**Request Body**（可选，`Content-Type: application/json`）

```json
{
  "stemText": "可选的初始题干文本"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `stemText` | `string` | 否 | 初始题干文本，可稍后通过 updateStem 设置 |

**Response** `201 Created`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DRAFT"
}
```

> `Location` 响应头包含新资源 URI：`/api/questions/550e8400-...`

---

### `PUT /api/questions/{questionUuid}/stem`

设置或更新题干 XML 文本。服务端强制执行 XML Schema 校验，不合规则返回 `422`。可反复调用以覆盖题干内容。

**Path Parameters**

| 参数 | 类型 | 说明 |
|------|------|------|
| `questionUuid` | `string` | 题目 UUID |

**Request Body**

```json
{
  "stemXml": "<stem><p>这是一道数学题</p></stem>"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `stemXml` | `string` | **是** | 符合 XML Schema 的题干文本 |

**XML Schema 支持的标签**: `<stem>`, `<p>`, `<equation>`, `<figure>`, `<table>`, `<blank>`, `<choice-group>`

**Response** `200 OK`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DRAFT"
}
```

**错误场景**

| 状态码 | code | 原因 |
|--------|------|------|
| `400` | `REQUEST_VALIDATION_FAILED` | `stemXml` 为空 |
| `404` | `QUESTION_NOT_FOUND` | 题目不存在或不属于当前用户 |
| `422` | `STEM_XML_INVALID` | XML 校验失败（详见 `details` 字段） |

---

### `POST /api/questions/{questionUuid}/answers`

为指定题目添加一条答案。

**Path Parameters**

| 参数 | 类型 | 说明 |
|------|------|------|
| `questionUuid` | `string` | 题目 UUID |

**Request Body**

```json
{
  "latexText": "x = \\frac{-b \\pm \\sqrt{b^2-4ac}}{2a}"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `latexText` | `string` | **是** | LaTeX 格式答案文本 |

**Response** `201 Created`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DRAFT"
}
```

---

### `PUT /api/questions/{questionUuid}/answers/{answerUuid}`

编辑指定答案的文本内容。

**Path Parameters**

| 参数 | 类型 | 说明 |
|------|------|------|
| `questionUuid` | `string` | 题目 UUID |
| `answerUuid` | `string` | 答案 UUID |

**Request Body**

```json
{
  "latexText": "x = 2"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `latexText` | `string` | **是** | 更新后的 LaTeX 答案文本 |

**Response** `200 OK`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "DRAFT"
}
```

**错误场景**

| 状态码 | code | 原因 |
|--------|------|------|
| `404` | `QUESTION_NOT_FOUND` | 题目不存在或不属于当前用户 |
| `404` | `ANSWER_NOT_FOUND` | 答案不存在或不属于该题目 |

---

### `DELETE /api/questions/{questionUuid}/answers/{answerUuid}`

删除指定答案。**至少保留一条答案**——当题目仅剩最后一条答案时，删除请求将被拒绝。

**Path Parameters**

| 参数 | 类型 | 说明 |
|------|------|------|
| `questionUuid` | `string` | 题目 UUID |
| `answerUuid` | `string` | 答案 UUID |

**Response** `204 No Content`

**错误场景**

| 状态码 | code | 原因 |
|--------|------|------|
| `404` | `QUESTION_NOT_FOUND` | 题目不存在或不属于当前用户 |
| `404` | `ANSWER_NOT_FOUND` | 答案不存在或不属于该题目 |
| `422` | `ANSWER_DELETE_LAST_NOT_ALLOWED` | 不能删除最后一条答案 |

---

### `POST /api/questions/{questionUuid}/complete`

将题目状态从 `DRAFT` 标记为 `READY`（完成）。前置条件：题干不为空 **且** 至少有一条答案。

**Response** `200 OK`

```json
{
  "questionUuid": "550e8400-e29b-41d4-a716-446655440000",
  "status": "READY"
}
```

**错误场景**

| 状态码 | code | 原因 |
|--------|------|------|
| `404` | `QUESTION_NOT_FOUND` | 题目不存在 |
| `422` | `QUESTION_COMPLETE_VALIDATION_FAILED` | 缺少 stemText 或 answers（`details.missingFields` 列出缺失项） |

---

### `DELETE /api/questions/{questionUuid}`

删除草稿题目。**仅限 `DRAFT` 状态且无答案**的题目。

**Response** `204 No Content`

**错误场景**

| 状态码 | code | 原因 |
|--------|------|------|
| `404` | `QUESTION_NOT_FOUND` | 题目不存在 |
| `422` | `QUESTION_DELETE_NOT_ALLOWED` | 非 DRAFT 状态或已有答案 |

---

## OCR 任务接口

### `POST /api/questions/{questionUuid}/ocr-tasks`

提交一个 OCR 识别任务。任务异步执行，结果通过 **WebSocket** 推送给客户端。

**Path Parameters**

| 参数 | 类型 | 说明 |
|------|------|------|
| `questionUuid` | `string` | 题目 UUID |

**Request Body**

```json
{
  "bizType": "QUESTION_STEM",
  "imageBase64": "BASE64_ENCODED_IMAGE_DATA..."
}
```

| 字段 | 类型 | 必填 | 校验 | 说明 |
|------|------|------|------|------|
| `bizType` | `string` | **是** | 必须为 `QUESTION_STEM` 或 `ANSWER_CONTENT` | OCR 业务类型 |
| `imageBase64` | `string` | **是** | `@NotBlank` | Base64 编码的图片数据 |

**Response** `202 Accepted`

```json
{
  "taskUuid": "770e8400-e29b-41d4-a716-446655440002",
  "status": "PENDING"
}
```

> 任务提交后为 `PENDING` 状态。OCR 服务完成后，question-service 内部自动将任务标记为 `CONFIRMED`（成功）或 `FAILED`（失败），并通过 WebSocket 推送结果。

---

## 标签接口

### `GET /api/tags`

获取系统标签目录，包含所有主分类及其可选标签，以及自定义二级标签分类信息。

**Response** `200 OK`

```json
{
  "mainCategories": [
    {
      "categoryCode": "SUBJECT",
      "categoryName": "学科",
      "options": [
        { "tagCode": "MATH", "tagName": "数学" },
        { "tagCode": "PHYSICS", "tagName": "物理" },
        { "tagCode": "UNCATEGORIZED", "tagName": "未分类" }
      ]
    },
    {
      "categoryCode": "DIFFICULTY",
      "categoryName": "难度",
      "options": [
        { "tagCode": "EASY", "tagName": "简单" },
        { "tagCode": "MEDIUM", "tagName": "中等" },
        { "tagCode": "HARD", "tagName": "困难" }
      ]
    }
  ],
  "secondaryCategoryCode": "SECONDARY_CUSTOM",
  "secondaryCategoryName": "自定义标签"
}
```

---

## WebSocket 推送

**连接端点**: WebSocket 连接由 gateway 层代理，question-service 内部通过 `QuestionWsHandler` 管理会话。

**消息格式**（JSON，服务端 → 客户端）：

```json
{
  "event": "<事件名>",
  "payload": { ... }
}
```

### OCR 成功事件

```json
{
  "event": "ocr.task.succeeded",
  "payload": {
    "taskUuid": "770e8400-e29b-41d4-a716-446655440002",
    "bizType": "QUESTION_STEM",
    "bizId": "550e8400-e29b-41d4-a716-446655440000",
    "recognizedText": "<stem><p>识别出的题干内容</p></stem>"
  }
}
```

### OCR 失败事件

```json
{
  "event": "ocr.task.failed",
  "payload": {
    "taskUuid": "770e8400-e29b-41d4-a716-446655440002",
    "bizType": "QUESTION_STEM",
    "bizId": "550e8400-e29b-41d4-a716-446655440000",
    "errorMessage": "Image too blurry to recognize"
  }
}
```

> 前端收到 `ocr.task.succeeded` 后，可将 `recognizedText` 展示给用户确认/修改，然后调用 `PUT /api/questions/{questionUuid}/stem`（题干）或 `POST .../answers`（答案）保存结果。

---

## 数据结构参考

### Request DTOs

#### CreateQuestionRequest

```typescript
{
  stemText?: string    // 可选，初始题干文本
}
```

#### UpdateStemRequest

```typescript
{
  stemXml: string      // 必填，XML 格式题干
}
```

#### CreateAnswerRequest

```typescript
{
  latexText: string    // 必填，LaTeX 答案文本
}
```

#### UpdateAnswerRequest

```typescript
{
  latexText: string    // 必填，更新的 LaTeX 答案文本
}
```

#### OcrTaskSubmitRequest

```typescript
{
  bizType: "QUESTION_STEM" | "ANSWER_CONTENT"   // 必填
  imageBase64: string                            // 必填，Base64 图片
}
```

### Response DTOs

#### QuestionStatusResponse

```typescript
{
  questionUuid: string
  status: "DRAFT" | "READY"
}
```

#### QuestionOverviewResponse

```typescript
{
  questionUuid: string
  status: "DRAFT" | "READY"
  stemText: string | null
  mainTags: QuestionMainTagResponse[]
  secondaryTags: string[]
  answerCount: number
  answers: AnswerOverviewResponse[]
  updatedAt: string                    // ISO 8601, e.g. "2026-03-02T14:30:00"
}
```

#### AnswerOverviewResponse

```typescript
{
  answerUuid: string
  answerType: "LATEX_TEXT"
  latexText: string
  sortOrder: number
  official: boolean
}
```

#### QuestionMainTagResponse

```typescript
{
  categoryCode: string
  categoryName: string
  tagCode: string
  tagName: string
}
```

#### OcrTaskAcceptedResponse

```typescript
{
  taskUuid: string
  status: "PENDING"
}
```

#### TagCatalogResponse

```typescript
{
  mainCategories: MainTagCategoryResponse[]
  secondaryCategoryCode: string
  secondaryCategoryName: string
}
```

#### MainTagCategoryResponse

```typescript
{
  categoryCode: string
  categoryName: string
  options: TagOptionResponse[]
}
```

#### TagOptionResponse

```typescript
{
  tagCode: string
  tagName: string
}
```

---

## 错误响应格式

所有业务错误和参数校验错误返回统一 JSON 结构：

```json
{
  "code": "ERROR_CODE",
  "message": "Human-readable error message",
  "traceId": "a1b2c3d4e5f6...",
  "details": { }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | `string` | 机器可读的错误码 |
| `message` | `string` | 错误描述 |
| `traceId` | `string` | 32 位追踪 ID，用于日志排查 |
| `details` | `object` | 补充信息，结构因错误而异 |

---

## 业务错误码一览

| code | HTTP 状态码 | 触发场景 | details 示例 |
|------|-------------|----------|-------------|
| `REQUEST_VALIDATION_FAILED` | `400` | 请求体校验失败（如 `@NotBlank` 未满足） | `{ "errors": "..." }` |
| `QUESTION_NOT_FOUND` | `404` | 题目不存在或不属于当前用户 | `{ "questionUuid": "...", "requestUser": "..." }` |
| `ANSWER_NOT_FOUND` | `404` | 答案不存在或不属于该题目 | `{ "questionUuid": "...", "answerUuid": "..." }` |
| `STEM_XML_INVALID` | `422` | 题干 XML 不符合 Schema 规范 | 取决于校验器输出 |
| `QUESTION_COMPLETE_VALIDATION_FAILED` | `422` | 完成题目时缺少必填项 | `{ "missingFields": ["stemText", "answers"] }` |
| `QUESTION_DELETE_NOT_ALLOWED` | `422` | 删除条件不满足 | `{ "questionUuid": "...", "status": "READY", "answerCount": 2 }` |
| `ANSWER_DELETE_LAST_NOT_ALLOWED` | `422` | 尝试删除最后一条答案 | `{ "questionUuid": "...", "answerUuid": "...", "answerCount": 1 }` |
