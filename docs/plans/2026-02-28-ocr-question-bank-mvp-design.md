# OCR 题库 MVP 设计文档

**日期:** 2026-02-28  
**状态:** 已确认（可进入实施计划）  
**范围级别:** MVP（可运行优先，企业化可扩展）

---

## 1. 目标与边界

### 1.1 MVP 目标

- 构建一条可运行闭环: 题目图片上传 -> GLM-OCR 异步识别 -> WebSocket 推送识别结果 -> 用户确认/修正 -> 题目草稿补全 -> 完成题目创建。
- 题号由系统自动生成 UUID。
- 答案支持一题多答案，MVP 先支持 `LaTeX 文本答案`。
- 题目完成后默认进入用户私有题库并立即可用。

### 1.2 非目标（本期不做或仅预留）

- 复杂公开审核流程（仅保留公开审核状态字段）。
- 多媒体答案全能力（先保留扩展字段和类型）。
- 复杂协作权限体系（先保留用户-题目关系表骨架）。

---

## 2. 服务架构

### 2.1 服务拆分

- `gateway-service`
  - 统一入口，JWT 校验，路由转发。
- `auth-service`
  - 登录与身份鉴权。
- `question-service`（新增）
  - 题目草稿、答案、标签、状态机、WS 推送、OCR 结果消费。
- `ocr-service`（新增）
  - 封装 GLM-OCR 对接，管理 OCR 任务，异步识别并回传结果。
- `libs/common-contract`
  - 共享事件契约与 DTO。

### 2.2 通信方式

- 同步 RPC: `question-service` 使用 OpenFeign 调用 `ocr-service` 的内部提交任务接口。
- 异步消息: RabbitMQ 传递 OCR 任务/结果事件。
- 实时反馈: `question-service` 通过 WebSocket 单播推送 OCR 状态与结果。

---

## 3. 核心业务流程

1. 用户创建题目草稿，系统分配 `question_uuid`。
2. 用户上传题目图片，`question-service` 通过 Feign 调 `ocr-service` 创建 OCR 任务。
3. `ocr-service` 投递 OCR 任务到 MQ，Worker 消费并调用本地 Docker GLM-OCR。
4. 识别完成后，`ocr-service` 发布 OCR 结果事件。
5. `question-service` 消费结果并通过 WS 推送给该用户。
6. 用户确认/修正 OCR 文本，写回题干或答案。
7. 用户补齐必要信息后触发完成创建；满足条件则 `DRAFT -> READY`。

---

## 4. 状态机设计

### 4.1 题目状态

- `DRAFT`: 草稿，未完成。
- `READY`: 完成，可在私有题库使用。
- `ARCHIVED`: 归档（预留）。

完成条件:
- `stem_text` 非空；
- 至少 1 条有效答案（MVP 为 LaTeX 文本）。

### 4.2 可见性状态

- `PRIVATE`（默认）
- `PUBLIC_PENDING_REVIEW`（预留）
- `PUBLIC`（预留）

### 4.3 OCR 任务状态

- `PENDING`
- `PROCESSING`
- `SUCCESS`
- `FAILED`
- `CONFIRMED`（用户确认后）

---

## 5. 企业化数据库模型（MySQL）

说明:
- 内部关联主键用 `BIGINT`，外部业务标识使用 `UUID`。
- 全表建议含 `created_at`, `updated_at`, `deleted_at`, `version`。

### 5.1 题目与答案

- `q_question`
  - `id` BIGINT PK
  - `question_uuid` CHAR(36) UNIQUE NOT NULL
  - `owner_user_id` BIGINT NOT NULL
  - `stem_text` TEXT NULL
  - `status` VARCHAR(32) NOT NULL
  - `visibility` VARCHAR(32) NOT NULL DEFAULT 'PRIVATE'
  - `difficulty` VARCHAR(32) NULL
  - `source_type` VARCHAR(32) NULL
  - `remark` VARCHAR(512) NULL

- `q_answer`
  - `id` BIGINT PK
  - `answer_uuid` CHAR(36) UNIQUE NOT NULL
  - `question_id` BIGINT NOT NULL
  - `answer_type` VARCHAR(32) NOT NULL  (MVP: LATEX_TEXT)
  - `latex_text` TEXT NULL
  - `sort_order` INT NOT NULL DEFAULT 1
  - `is_official` BOOLEAN NOT NULL DEFAULT FALSE

- `q_question_asset`
  - `id` BIGINT PK
  - `asset_uuid` CHAR(36) UNIQUE NOT NULL
  - `question_id` BIGINT NOT NULL
  - `asset_type` VARCHAR(32) NOT NULL  (QUESTION_IMAGE/ANSWER_IMAGE/OTHER)
  - `storage_url` VARCHAR(1024) NOT NULL
  - `ocr_task_id` BIGINT NULL
  - `meta_json` JSON NULL

### 5.2 标签体系

- `q_tag`
  - `id` BIGINT PK
  - `tag_code` VARCHAR(128) UNIQUE NULL
  - `tag_name` VARCHAR(255) NOT NULL
  - `tag_type` VARCHAR(32) NOT NULL  (MAIN_GRADE/MAIN_KNOWLEDGE/CUSTOM)

- `q_question_tag_rel`
  - `id` BIGINT PK
  - `question_id` BIGINT NOT NULL
  - `tag_id` BIGINT NOT NULL
  - `rel_type` VARCHAR(32) NOT NULL  (PRIMARY/SECONDARY)
  - `created_by` BIGINT NOT NULL

### 5.3 用户-题目关系（M:N 预留）

- `q_user_question_rel`
  - `id` BIGINT PK
  - `user_id` BIGINT NOT NULL
  - `question_id` BIGINT NOT NULL
  - `rel_role` VARCHAR(32) NOT NULL  (OWNER/EDITOR/VIEWER)
  - `source` VARCHAR(32) NOT NULL  (PRIVATE/SHARED/TEAM)

### 5.4 OCR 任务与确认

- `q_ocr_task`
  - `id` BIGINT PK
  - `task_uuid` CHAR(36) UNIQUE NOT NULL
  - `biz_type` VARCHAR(32) NOT NULL  (QUESTION_STEM/ANSWER_CONTENT)
  - `biz_id` CHAR(36) NOT NULL
  - `image_url` VARCHAR(1024) NOT NULL
  - `status` VARCHAR(32) NOT NULL
  - `provider` VARCHAR(64) NOT NULL DEFAULT 'GLM_OCR'
  - `request_payload` JSON NULL
  - `raw_response` JSON NULL
  - `error_msg` VARCHAR(1024) NULL

- `q_ocr_confirm_snapshot`
  - `id` BIGINT PK
  - `task_id` BIGINT NOT NULL
  - `confirmed_text` TEXT NOT NULL
  - `confirmed_by` BIGINT NOT NULL
  - `confirmed_at` DATETIME NOT NULL

### 5.5 组卷骨架（预留）

- `q_paper`
  - `id` BIGINT PK
  - `paper_uuid` CHAR(36) UNIQUE NOT NULL
  - `owner_user_id` BIGINT NOT NULL
  - `title` VARCHAR(255) NOT NULL
  - `status` VARCHAR(32) NOT NULL

- `q_paper_question_rel`
  - `id` BIGINT PK
  - `paper_id` BIGINT NOT NULL
  - `question_id` BIGINT NOT NULL
  - `seq_no` INT NOT NULL
  - `score` DECIMAL(8,2) NULL
  - `section_name` VARCHAR(255) NULL

### 5.6 索引与约束建议

- 唯一索引: `question_uuid`, `answer_uuid`, `task_uuid`, `paper_uuid`。
- 组合索引:
  - `(owner_user_id, status, visibility, updated_at)` on `q_question`
  - `(question_id, sort_order)` on `q_answer`
  - `(question_id, rel_type)` on `q_question_tag_rel`
  - `(user_id, rel_role)` on `q_user_question_rel`
  - `(biz_type, biz_id, status, created_at)` on `q_ocr_task`
- 业务约束:
  - 仅满足完成条件可转 `READY`；
  - 默认 `visibility=PRIVATE`。

---

## 6. REST API 规范（重点）

### 6.1 设计原则

- 使用名词复数资源: `/questions`, `/answers`, `/ocr-tasks`。
- 使用 HTTP 动词表达操作语义，避免动词型路径。
- 使用一致状态码、错误体、分页和过滤规范。
- 只在必要时引入动作型子资源（如 `/complete`）。

### 6.2 路由与动作（MVP）

- `POST /api/questions`
  - 创建题目草稿（返回 `201 Created` + `Location`）。
- `GET /api/questions/{questionUuid}`
  - 查询题目详情。
- `PATCH /api/questions/{questionUuid}`
  - 更新题干/标签/难度等可变字段。
- `POST /api/questions/{questionUuid}/answers`
  - 新增答案。
- `PATCH /api/answers/{answerUuid}`
  - 更新答案内容。
- `POST /api/questions/{questionUuid}/ocr-tasks`
  - 创建题目 OCR 任务（body 指明 `bizType` 与图片地址）。
- `POST /api/answers/{answerUuid}/ocr-tasks`
  - 创建答案 OCR 任务。
- `POST /api/ocr-tasks/{taskUuid}/confirmations`
  - 提交 OCR 确认文本（可含修正后文本）。
- `POST /api/questions/{questionUuid}/complete`
  - 执行完成校验与状态流转。

### 6.3 状态码规范

- `200 OK`: 查询、普通更新成功。
- `201 Created`: 新资源创建成功。
- `202 Accepted`: 异步任务受理成功（OCR 任务推荐）。
- `400 Bad Request`: 参数校验失败。
- `401 Unauthorized`: 未登录或 token 无效。
- `403 Forbidden`: 资源无权限。
- `404 Not Found`: 资源不存在。
- `409 Conflict`: 状态冲突、幂等冲突、重复提交。
- `422 Unprocessable Entity`: 业务规则不满足（如完成条件未达成）。
- `500 Internal Server Error`: 非预期服务错误。

### 6.4 统一错误响应

```json
{
  "code": "QUESTION_COMPLETE_VALIDATION_FAILED",
  "message": "stem_text is required and at least one answer is required",
  "traceId": "9f4b6f7f9c2f4f7a",
  "details": {
    "missingFields": ["stemText", "answers"]
  }
}
```

### 6.5 幂等与并发控制

- OCR 任务创建支持幂等键: `Idempotency-Key`。
- 写接口支持乐观锁字段 `version`（409 冲突）。
- 异步回写按 `task_uuid` 去重消费。

---

## 7. WebSocket 事件协议

连接:
- `GET /ws/questions`，JWT 鉴权，服务端按 `userId` 单播。

服务端推送事件:
- `ocr.task.accepted`
- `ocr.task.processing`
- `ocr.task.succeeded`
- `ocr.task.failed`

事件示例:

```json
{
  "event": "ocr.task.succeeded",
  "taskUuid": "3f5956e4-9890-4f48-b7cb-f61fb4b75a6e",
  "bizType": "QUESTION_STEM",
  "bizId": "cdf61c86-9868-44b8-bae8-fd47a43d537f",
  "recognizedText": "x^2 + y^2 = 1",
  "timestamp": "2026-02-28T11:20:30Z"
}
```

---

## 8. MQ 与 Redis 设计

### 8.1 RabbitMQ

- Exchange: `qforge.ocr` (topic)
- Routing Key:
  - `ocr.task.created`
  - `ocr.task.result`
  - `ocr.task.failed`
- Queue:
  - `qforge.ocr.task.q`
  - `qforge.ocr.result.q`
  - `qforge.ocr.dlq`

### 8.2 Redis

- `ocr:task:{taskUuid}` 缓存任务摘要（短 TTL）。
- `ws:user:{userId}:sessions` 维护活跃会话列表。
- `idempotency:ocr:{key}` 防重键。

---

## 9. 安全与审计

- 所有题库数据按 `owner_user_id` 做强隔离。
- 私有题默认可用；公开流程仅保留状态，不实现审核流。
- 保留 OCR 原始响应和确认快照，支持审计与回溯。

---

## 10. 交付标准（MVP Done Definition）

- 能创建题目草稿并自动生成 UUID。
- 能创建 OCR 任务并异步处理 GLM-OCR。
- 前端可通过 WS 收到 OCR 结果与失败消息。
- 用户可确认 OCR 内容并完成题目创建。
- `READY` 校验严格执行: 题干非空 + 至少一条答案。
- REST API 满足统一规范（资源命名、状态码、错误体、幂等）。

