# Question Service API Reference

**Base URL**: `http://localhost:8089/api`  
**Auth Header**: `X-Auth-User` (defaults to `anonymous`)

---

## Questions

### 1. List Questions

```
GET /api/questions
```

Lists all questions owned by the authenticated user.

**Response**: `200 OK`

```json
[
  {
    "questionUuid": "uuid",
    "status": "DRAFT | READY",
    "stemText": "<stem version=\"1\">...</stem>",
    "mainTags": [
      { "categoryCode": "MAIN_GRADE", "categoryName": "年级", "tagCode": "UNCATEGORIZED", "tagName": "未分类" }
    ],
    "secondaryTags": ["标签1", "标签2"],
    "difficulty": 0.65,
    "answerCount": 2,
    "answers": [
      { "answerUuid": "uuid", "answerType": "LATEX_TEXT", "latexText": "...", "sortOrder": 1, "official": false }
    ],
    "updatedAt": "2026-03-02T10:00:00"
  }
]
```

---

### 2. Create Draft

```
POST /api/questions
```

Creates a new draft question. Request body is optional.

**Request Body** (optional):

```json
{ "stemText": "<stem version=\"1\">...</stem>" }
```

**Response**: `201 Created`

```json
{ "questionUuid": "uuid", "status": "DRAFT" }
```

---

### 3. Update Stem

```
PUT /api/questions/{questionUuid}/stem
```

Updates stem text (validates XML schema).

**Request Body**:

```json
{ "stemXml": "<stem version=\"1\">...</stem>" }
```

**Response**: `200 OK`

```json
{ "questionUuid": "uuid", "status": "DRAFT" }
```

---

### 4. Add Answer

```
POST /api/questions/{questionUuid}/answers
```

Adds a new answer to the question.

**Request Body**:

```json
{ "latexText": "$x = 2$" }
```

**Response**: `201 Created`

```json
{ "questionUuid": "uuid", "status": "DRAFT" }
```

---

### 5. Complete Question

```
POST /api/questions/{questionUuid}/complete
```

Transitions the question from DRAFT to READY. Requires stem + at least 1 answer.

**Response**: `200 OK`

```json
{ "questionUuid": "uuid", "status": "READY" }
```

**Errors**:
- `422 QUESTION_COMPLETE_VALIDATION_FAILED` — missing stemText or answers.

---

### 6. Submit OCR Task

```
POST /api/questions/{questionUuid}/ocr-tasks
```

Submits an image for OCR recognition (async via MQ).

**Request Body**:

```json
{
  "bizType": "QUESTION_STEM | ANSWER_CONTENT",
  "imageBase64": "data:image/png;base64,..."
}
```

**Response**: `202 Accepted`

```json
{ "taskUuid": "uuid", "status": "PENDING" }
```

**WebSocket Events** (after processing):
- `ocr.task.succeeded` → `{ taskUuid, bizType, bizId, recognizedText }`
- `ocr.task.failed` → `{ taskUuid, bizType, bizId, errorMessage }`

---

### 7. Delete Draft Question

```
DELETE /api/questions/{questionUuid}
```

Deletes a draft question (must have no answers).

**Response**: `204 No Content`

**Errors**:
- `422 QUESTION_DELETE_NOT_ALLOWED` — non-draft or has answers.

---

### 8. Update Tags

```
PUT /api/questions/{questionUuid}/tags
```

Replaces all secondary (custom) tags for the question.

**Request Body**:

```json
{ "tags": ["二次函数", "配方法", "判别式"] }
```

**Response**: `200 OK`

```json
{ "questionUuid": "uuid", "status": "READY" }
```

---

### 9. Update Difficulty

```
PUT /api/questions/{questionUuid}/difficulty
```

Sets the difficulty P-value (0.00–1.00).

**Request Body**:

```json
{ "difficulty": 0.65 }
```

**Response**: `200 OK`

```json
{ "questionUuid": "uuid", "status": "READY" }
```

**Validation**: `difficulty` must be between 0.00 and 1.00 inclusive.

---

### 10. Request AI Analysis

```
POST /api/questions/{questionUuid}/ai-analysis
```

Triggers async AI analysis to generate suggested tags + difficulty P-value. Requires stem text to be present.

**Request Body**: none

**Response**: `202 Accepted`

**Errors**:
- `422 AI_ANALYSIS_MISSING_STEM` — stem text is empty.

**WebSocket Events** (after processing):
- `ai.analysis.succeeded` → `{ questionUuid, suggestedTags: [...], suggestedDifficulty: 0.65, reasoning: "..." }`
- `ai.analysis.failed` → `{ questionUuid, errorMessage: "..." }`

---

## Tags

### 11. Get Tag Catalog

```
GET /api/tags
```

Returns the full tag catalog (main categories + secondary category info).

**Response**: `200 OK`

```json
{
  "mainCategories": [
    {
      "categoryCode": "MAIN_GRADE",
      "categoryName": "年级",
      "options": [
        { "tagCode": "UNCATEGORIZED", "tagName": "未分类" }
      ]
    }
  ],
  "secondaryCategoryCode": "SECONDARY_CUSTOM",
  "secondaryCategoryName": "副标签"
}
```

---

## WebSocket

**Endpoint**: `ws://localhost:8089/ws/questions?user={username}`

**Message Format**:

```json
{
  "event": "event.name",
  "payload": { ... }
}
```

**Events**:

| Event | Trigger | Payload |
|-------|---------|---------|
| `ocr.task.succeeded` | OCR 识别成功 | `taskUuid, bizType, bizId, recognizedText` |
| `ocr.task.failed` | OCR 识别失败 | `taskUuid, bizType, bizId, errorMessage` |
| `ai.analysis.succeeded` | AI 分析完成 | `questionUuid, suggestedTags, suggestedDifficulty, reasoning` |
| `ai.analysis.failed` | AI 分析失败 | `questionUuid, errorMessage` |

---

## Difficulty P-Value Reference

| P-Value Range | Level | Color |
|---------------|-------|-------|
| 0.90–1.00 | 入门 (Trivial) | `#4CAF50` green |
| 0.70–0.89 | 简单 (Easy) | `#8BC34A` light green |
| 0.30–0.69 | 中等 (Medium) | `#FF9800` orange |
| 0.10–0.29 | 困难 (Hard) | `#F44336` red |
| 0.00–0.09 | 专家 (Expert) | `#9C27B0` purple |
