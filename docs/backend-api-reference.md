# QForge 后端 API 参考文档

> 自动生成于 2026-03-08，基于后端 Java 源码梳理

## 服务总览

| 服务             | 端口  | 活跃 Controller | 基础路径                          |
| ---------------- | ----- | --------------- | --------------------------------- |
| gateway-service  | 8080  | 1               | `/public`, `/gateway`             |
| auth-service     | 8081  | 1               | `/auth`                           |
| ocr-service      | 8083  | 1               | `/internal/ocr/tasks` (内部)      |
| question-service | 8082  | 3               | `/api/questions`, `/api/exam-parse`, `/api/tags` |
| persist-service  | 8084  | 0               | 无 HTTP 端点（纯 MQ 消费者）     |

**公共 Header**: 所有 `/api/*` 端点要求 `X-Auth-User` header（由 gateway 在 JWT 验证后注入）。

---

## 1. gateway-service

### `GET /public/ping`
- **说明**: 公开健康检查
- **响应**: `{ "message": "gateway ok" }`

### `GET /gateway/ping`
- **Header**: `X-Auth-User`
- **响应**: `{ "message": "secured gateway ok", "username": "..." }`

---

## 2. auth-service

### `POST /auth/login`
- **请求体**: `LoginRequest`
  | 字段       | 类型     | 校验       |
  | ---------- | -------- | ---------- |
  | `username` | `String` | `@NotBlank` |
  | `password` | `String` | `@NotBlank` |
- **响应**: `LoginResponse`
  | 字段              | 类型     |
  | ----------------- | -------- |
  | `accessToken`     | `String` |
  | `expiresInSeconds`| `long`   |
  | `tokenType`       | `String` |

### `GET /auth/me`
- **说明**: 获取当前登录用户（Spring Security Principal）
- **响应**: `{ "username": "..." }`

---

## 3. question-service — 题目 CRUD

### `GET /api/questions`
- **说明**: 获取当前用户所有题目
- **响应**: `List<QuestionOverviewResponse>`

### `POST /api/questions`
- **说明**: 新建题目
- **请求体**: `CreateQuestionRequest`（可选，允许 null body）
  | 字段       | 类型     | 校验 | 说明     |
  | ---------- | -------- | ---- | -------- |
  | `stemText` | `String` | 无   | 可选初始题干 |
- **响应**: `QuestionStatusResponse` (HTTP 201)

### `DELETE /api/questions/{questionUuid}`
- **说明**: 删除题目
- **响应**: HTTP 204

### `PUT /api/questions/{questionUuid}/stem` ⭐ 确认题干
- **Path**: `questionUuid`
- **请求体**: `UpdateStemRequest`
  | 字段           | 类型                              | 校验                                  | 说明                                  |
  | -------------- | --------------------------------- | ------------------------------------- | ------------------------------------- |
  | `stemXml`      | `String`                          | `@NotBlank`                           | 题干 XML，如 `<stem version="1"><p>...</p></stem>` |
  | `inlineImages` | `Map<String, InlineImageEntry>`   | 无（可 null，service 层最多 10 张）   | key=前端 ref（如 `img-1`）            |

  **`InlineImageEntry`**:
  | 字段        | 类型     | 校验       | 说明                        |
  | ----------- | -------- | ---------- | --------------------------- |
  | `imageData` | `String` | `@NotBlank` | base64 编码（无 data: 前缀） |
  | `mimeType`  | `String` | 无         | 可选，默认 `image/png`      |

  约束: 单张 base64 解码后 ≤ 512KB，最多 10 张图片。

- **响应**: `QuestionStatusResponse`

### `POST /api/questions/{questionUuid}/answers` ⭐ 添加答案
- **Path**: `questionUuid`
- **请求体**: `CreateAnswerRequest`
  | 字段           | 类型                              | 校验       |
  | -------------- | --------------------------------- | ---------- |
  | `latexText`    | `String`                          | `@NotBlank` |
  | `inlineImages` | `Map<String, InlineImageEntry>`   | 无（可选） |
- **响应**: `AddAnswerResponse` (HTTP 201)
  | 字段           | 类型     |
  | -------------- | -------- |
  | `questionUuid` | `String` |
  | `status`       | `String` |
  | `answerUuid`   | `String` |

### `PUT /api/questions/{questionUuid}/answers/{answerUuid}`
- **说明**: 更新答案
- **请求体**: `UpdateAnswerRequest`
  | 字段           | 类型                              | 校验       |
  | -------------- | --------------------------------- | ---------- |
  | `latexText`    | `String`                          | `@NotBlank` |
  | `inlineImages` | `Map<String, InlineImageEntry>`   | 无（可选） |
- **响应**: `QuestionStatusResponse`

### `DELETE /api/questions/{questionUuid}/answers/{answerUuid}`
- **说明**: 删除答案
- **响应**: HTTP 204

### `POST /api/questions/{questionUuid}/complete` ⭐ 完成录入
- **Path**: `questionUuid`
- **请求体**: 无
- **响应**: `QuestionStatusResponse`

### `GET /api/questions/{questionUuid}/assets`
- **说明**: 获取题目所有图片资源
- **响应**: `List<QuestionAssetResponse>`
  | 字段        | 类型     | 说明            |
  | ----------- | -------- | --------------- |
  | `assetUuid` | `String` |                 |
  | `refKey`    | `String` | 前端引用 key     |
  | `imageData` | `String` | base64 编码图片  |
  | `mimeType`  | `String` |                 |

### `PUT /api/questions/{questionUuid}/tags`
- **请求体**: `UpdateTagsRequest`
  | 字段   | 类型           | 校验       |
  | ------ | -------------- | ---------- |
  | `tags` | `List<String>` | `@NotNull` |
- **响应**: `QuestionStatusResponse`

### `PUT /api/questions/{questionUuid}/difficulty`
- **请求体**: `UpdateDifficultyRequest`
  | 字段         | 类型         | 校验                                    |
  | ------------ | ------------ | --------------------------------------- |
  | `difficulty` | `BigDecimal` | `@NotNull`, `@DecimalMin("0.00")`, `@DecimalMax("1.00")` |
- **响应**: `QuestionStatusResponse`

### `POST /api/questions/{questionUuid}/ocr-tasks`
- **请求体**: `OcrTaskSubmitRequest`
  | 字段          | 类型     | 校验                                          |
  | ------------- | -------- | --------------------------------------------- |
  | `bizType`     | `String` | `@NotBlank`, `@Pattern("QUESTION_STEM\|ANSWER_CONTENT")` |
  | `imageBase64` | `String` | `@NotBlank`                                   |
- **响应**: `OcrTaskAcceptedResponse` (HTTP 202)
  | 字段       | 类型     |
  | ---------- | -------- |
  | `taskUuid` | `String` |
  | `status`   | `String` |

### `POST /api/questions/{questionUuid}/ai-analysis`
- **说明**: 提交 AI 分析任务
- **请求体**: 无
- **响应**: `AiTaskAcceptedResponse` (HTTP 202)
  | 字段       | 类型     |
  | ---------- | -------- |
  | `taskUuid` | `String` |

### `GET /api/questions/{questionUuid}/ai-tasks`
- **说明**: 获取 AI 分析任务列表
- **响应**: `List<AiTaskResponse>`

### `PUT /api/questions/{questionUuid}/ai-tasks/{taskUuid}/apply`
- **说明**: 应用 AI 推荐结果
- **请求体**: `ApplyAiRecommendationRequest`
  | 字段         | 类型           | 校验 |
  | ------------ | -------------- | ---- |
  | `tags`       | `List<String>` | 无   |
  | `difficulty` | `BigDecimal`   | 无   |
- **响应**: `QuestionStatusResponse`

---

## 4. question-service — 试卷解析

### `POST /api/exam-parse/tasks`
- **说明**: 上传 PDF 文件解析试卷
- **Content-Type**: `multipart/form-data`
- **参数**:
  | 参数            | 类型             | 说明           |
  | --------------- | ---------------- | -------------- |
  | `files`         | `MultipartFile[]`| PDF 文件       |
  | `hasAnswerHint` | `boolean`        | 默认 false     |
- **响应**: `{ taskUuid, status, fileCount, message }` (HTTP 202)

### `GET /api/exam-parse/tasks`
- **响应**: `List<ExamParseTask>`

### `GET /api/exam-parse/tasks/{taskUuid}`
- **响应**: `{ task: ExamParseTask, questions: List<ExamParseQuestion> }`

### `PUT /api/exam-parse/tasks/{taskUuid}/questions/{seqNo}`
- **请求体**: `Map<String, String>`（可修改 stemXml、answerXml 等字段）
- **响应**: `ExamParseQuestion`

### `POST /api/exam-parse/tasks/{taskUuid}/confirm`
- **说明**: 确认整卷所有题目
- **响应**: `{ taskUuid, confirmedCount, message }`

### `POST /api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/confirm`
- **说明**: 确认单题
- **响应**: `{ taskUuid, seqNo, questionUuid, message }`

### `POST /api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/skip`
- **说明**: 跳过单题
- **响应**: `{ taskUuid, seqNo, message }`

### `POST /api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/unskip`
- **说明**: 取消跳过
- **响应**: `{ taskUuid, seqNo, message }`

### `DELETE /api/exam-parse/tasks/{taskUuid}`
- **响应**: HTTP 204

---

## 5. question-service — 标签

### `GET /api/tags`
- **响应**: `TagCatalogResponse`
  | 字段                   | 类型                            |
  | ---------------------- | ------------------------------- |
  | `mainCategories`       | `List<MainTagCategoryResponse>` |
  | `secondaryCategoryCode`| `String`                        |
  | `secondaryCategoryName`| `String`                        |

---

## 6. 公共响应 DTO

### `QuestionStatusResponse`
| 字段           | 类型     |
| -------------- | -------- |
| `questionUuid` | `String` |
| `status`       | `String` |

### `QuestionOverviewResponse`
| 字段           | 类型                             |
| -------------- | -------------------------------- |
| `questionUuid` | `String`                         |
| `status`       | `String`                         |
| `stemText`     | `String`                         |
| `mainTags`     | `List<QuestionMainTagResponse>`  |
| `secondaryTags`| `List<String>`                   |
| `difficulty`   | `BigDecimal`                     |
| `answerCount`  | `long`                           |
| `answers`      | `List<AnswerOverviewResponse>`   |
| `updatedAt`    | `LocalDateTime`                  |

---

## 7. MQ 事件（服务间通信）

| 事件                        | 方向                         | 说明         |
| --------------------------- | ---------------------------- | ------------ |
| `OcrTaskCreatedEvent`       | question → ocr               | OCR 任务创建  |
| `OcrTaskResultEvent`        | ocr → question               | OCR 结果回调  |
| `AiAnalysisTaskCreatedEvent`| question → ocr               | AI 分析任务   |
| `AiAnalysisResultEvent`     | ocr → question               | AI 结果回调   |
| `DbWriteBackEvent`          | 各服务 → persist             | DB 异步写入   |
| `ExamParseTaskCreatedEvent` | question → ocr               | 试卷解析任务  |
| `ExamParseQuestionResultEvent` | ocr → question            | 单题解析结果  |
| `ExamParseCompletedEvent`   | ocr → question               | 整卷解析完成  |
