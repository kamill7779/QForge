# 后端服务接口总览

更新时间：2026-03-09

## 说明

- 本文基于当前仓库中的控制器、Feign 客户端、应用服务和 MQ 消费链路整理。
- 对外访问统一以 `gateway-service` 暴露的网关路径为准；服务间调用单独标注为“内部接口”。
- 大多数业务控制器通过 `X-Auth-User` 识别当前用户，实际值通常由认证链路注入。
- `question-service` 在代码目录中仍叫 `question-service`，但运行时/配置层实际已经作为 `question-core-service` 使用，本文统一按 `question-core-service` 记述。

## 1. gateway-service

`gateway-service` 本身只有健康接口，但它决定了前端实际访问路径。

### 1.1 自身接口

| 方法 | 对外路径 | 功能 |
| --- | --- | --- |
| `GET` | `/public/ping` | 公共健康检查 |
| `GET` | `/gateway/ping` | 网关健康检查 |

### 1.2 路由映射

| 网关路径 | 目标服务 | 备注 |
| --- | --- | --- |
| `/api/auth/**` | `auth-service` | 使用 `StripPrefix=1`，所以外部 `/api/auth/login` 会落到服务内 `/auth/login` |
| `/api/exam-parse/**` | `exam-parse-service` | 路径不改写 |
| `/api/exam-papers/**` | `exam-service` | 路径不改写 |
| `/api/question-types/**` | `exam-service` | 路径不改写 |
| `/api/question-basket/**` | `exam-service` | 路径不改写 |
| `/api/questions/**` | `question-core-service` | 路径不改写 |
| `/api/tags/**` | `question-core-service` | 路径不改写 |
| `/ws/questions/**` | `question-core-service` | WebSocket 转发 |

## 2. auth-service

服务内基路径：`/auth`

| 方法 | 网关路径 | 服务内路径 | 功能 |
| --- | --- | --- | --- |
| `POST` | `/api/auth/login` | `/auth/login` | 用户名密码登录，返回 JWT |
| `GET` | `/api/auth/me` | `/auth/me` | 返回当前登录用户名 |

## 3. question-core-service

### 3.1 对外业务接口

基路径：`/api/questions`

| 方法 | 路径 | 功能 | 关键说明 |
| --- | --- | --- | --- |
| `GET` | `/api/questions` | 查询当前用户题目总表 | 当前实现一次性返回全量，无分页 |
| `GET` | `/api/questions/page?page={page}&size={size}` | 分页查询当前用户题目概览 | 当前为 `web` 新增的缓冲分页入口，适合后端 100 / 前端 20 的分层展示 |
| `GET` | `/api/questions/{questionUuid}` | 查询单题详情 | 用于题目详情弹窗、轻量按需加载 |
| `GET` | `/api/questions/{questionUuid}/assets` | 获取题干内联图片资源 | 先读 Redis 短 TTL 缓存，再回退数据库 |
| `POST` | `/api/questions` | 创建题目草稿 | 可选初始题干 |
| `PUT` | `/api/questions/{questionUuid}/stem` | 更新题干 XML 和题干图片 | 会校验 XML，并同步 `q_question_asset` |
| `POST` | `/api/questions/{questionUuid}/complete` | 将题目标记为 `READY` | 需要题干存在且至少一条答案 |
| `POST` | `/api/questions/{questionUuid}/answers` | 新增答案 | 支持答案图片 |
| `PUT` | `/api/questions/{questionUuid}/answers/{answerUuid}` | 修改答案 | 支持答案图片同步 |
| `DELETE` | `/api/questions/{questionUuid}/answers/{answerUuid}` | 删除答案 | 禁止删到最后一条答案 |
| `POST` | `/api/questions/{questionUuid}/ocr-tasks` | 提交 OCR 任务 | `bizType` 为 `QUESTION_STEM` 或 `ANSWER_CONTENT` |
| `DELETE` | `/api/questions/{questionUuid}` | 删除单题 | 级联删除答案、标签、资产、任务记录 |
| `POST` | `/api/questions/batch-delete` | 批量删题 | 请求体字段 `questionUuids` |
| `PUT` | `/api/questions/{questionUuid}/tags` | 更新标签 | 已统一走 `QuestionTagAssignmentService` |
| `PUT` | `/api/questions/{questionUuid}/difficulty` | 更新难度值 | 同步题目摘要缓存失效 |
| `PUT` | `/api/questions/{questionUuid}/source` | 更新来源 | 同步题目摘要缓存失效 |
| `GET` | `/api/questions/sources` | 查询当前用户来源去重列表 | 已改为仓储层去重查询 |
| `POST` | `/api/questions/{questionUuid}/ai-analysis` | 发起 AI 标签/难度分析 | 异步任务，结果通过 MQ + WS 返回 |
| `GET` | `/api/questions/{questionUuid}/ai-tasks` | 查询 AI 任务列表 | 用于展示历史分析结果 |
| `PUT` | `/api/questions/{questionUuid}/ai-tasks/{taskUuid}/apply` | 应用 AI 推荐 | 应用标签和难度，并将任务标记为 `APPLIED` |

### 3.2 标签接口

基路径：`/api/tags`

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/tags` | 获取主标签目录与副标签分类元信息 |

### 3.3 内部接口

基路径：`/internal/questions`

| 方法 | 路径 | 调用方 | 功能 |
| --- | --- | --- | --- |
| `GET` | `/internal/questions/batch` | `exam-service` | 批量取题目摘要，用于试卷详情、试题篮组卷 |
| `POST` | `/internal/questions/batch-full` | `exam-service` | 批量取题目完整数据，用于导出 |
| `GET` | `/internal/questions/{questionUuid}/exists` | `exam-service` | 校验题目是否存在且归属指定用户 |
| `POST` | `/internal/questions/from-parse` | `exam-parse-service` | 将解析暂存题落为正式题目、答案、图片、标签 |

### 3.4 WebSocket

| 协议 | 路径 | 功能 |
| --- | --- | --- |
| `WS` | `/ws/questions?user={username}&token={token}` | OCR、AI、试卷解析等异步事件推送 |

## 4. exam-service

### 4.1 试题篮

基路径：`/api/question-basket`

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/question-basket` | 查询试题篮完整条目 |
| `GET` | `/api/question-basket/uuids` | 查询试题篮题目 UUID 列表 |
| `POST` | `/api/question-basket/{questionUuid}` | 加入试题篮 |
| `POST` | `/api/question-basket/{questionUuid}/toggle` | 在试题篮中切换 |
| `DELETE` | `/api/question-basket/{questionUuid}` | 单条移除 |
| `DELETE` | `/api/question-basket` | 清空试题篮 |

### 4.2 题型配置

基路径：`/api/question-types`

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/question-types` | 查询系统题型和用户自定义题型 |
| `POST` | `/api/question-types` | 新增自定义题型 |
| `PUT` | `/api/question-types/{id}` | 修改自定义题型 |
| `DELETE` | `/api/question-types/{id}` | 删除自定义题型 |

### 4.3 试卷

基路径：`/api/exam-papers`

| 方法 | 路径 | 功能 | 关键说明 |
| --- | --- | --- | --- |
| `GET` | `/api/exam-papers` | 查询当前用户试卷列表 | 已做批量聚合，避免旧版 N+1 |
| `POST` | `/api/exam-papers` | 新建空试卷 | 默认时长来自配置 |
| `POST` | `/api/exam-papers/from-basket` | 由试题篮一键创建试卷 | 默认分值来自配置 |
| `GET` | `/api/exam-papers/{paperUuid}` | 查询试卷详情 | 内部批量调用 `question-core-service` 摘要接口 |
| `PUT` | `/api/exam-papers/{paperUuid}` | 更新试卷元信息 | 标题、副标题、说明、时长、状态 |
| `DELETE` | `/api/exam-papers/{paperUuid}` | 删除试卷 | 逻辑上删除整卷实体 |
| `PUT` | `/api/exam-papers/{paperUuid}/content` | 原子保存整卷结构 | 先删旧 section/question，再写新结构 |
| `POST` | `/api/exam-papers/{paperUuid}/export/word` | 导出 Word | 通过 `export-sidecar` 执行导出 |

## 5. exam-parse-service

基路径：`/api/exam-parse`

| 方法 | 路径 | 功能 | 关键说明 |
| --- | --- | --- | --- |
| `POST` | `/api/exam-parse/tasks` | 创建试卷解析任务 | multipart 上传，参数 `files` + `hasAnswerHint` |
| `GET` | `/api/exam-parse/tasks` | 查询当前用户解析任务列表 | 返回任务状态、进度、题量等 |
| `GET` | `/api/exam-parse/tasks/{taskUuid}` | 查询任务详情 | 返回 `task` + `questions` |
| `PUT` | `/api/exam-parse/tasks/{taskUuid}/questions/{seqNo}` | 修改解析题 | 可改 `stemXml`、`answerXml`、题型、标签、难度 |
| `POST` | `/api/exam-parse/tasks/{taskUuid}/confirm` | 批量确认入库 | 仅处理 `PENDING` 题 |
| `POST` | `/api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/confirm` | 单题确认入库 | 落正式题后回写 `questionUuid` |
| `POST` | `/api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/skip` | 跳过题目 | `PENDING -> SKIPPED` |
| `POST` | `/api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/unskip` | 恢复题目 | `SKIPPED -> PENDING` |
| `DELETE` | `/api/exam-parse/tasks/{taskUuid}` | 删除任务与全部暂存数据 | 删除任务、源文件、暂存题 |

## 6. ocr-service

`ocr-service` 不直接服务前端页面，主要承担内部任务入口和 MQ 消费。

### 6.1 内部 HTTP 接口

基路径：`/internal/ocr/tasks`

| 方法 | 路径 | 调用方 | 功能 |
| --- | --- | --- | --- |
| `POST` | `/internal/ocr/tasks` | `question-core-service` | 创建 OCR 任务并返回 `taskUuid` |

### 6.2 关键 MQ 消费职责

| 消费者 | 输入 | 输出 |
| --- | --- | --- |
| `OcrTaskConsumer` | `OcrTaskCreatedEvent` | `OcrTaskResultEvent` + `DbWriteBackEvent(OCR_LOCAL)` |
| `AiAnalysisTaskConsumer` | `AiAnalysisTaskCreatedEvent` | `AiAnalysisResultEvent` + `DbWriteBackEvent(AI_LOCAL)` |
| `ExamParseTaskConsumer` | `ExamParseTaskCreatedEvent` | 逐题 `ExamParseQuestionResultEvent` + `ExamParseCompletedEvent` |

## 7. persist-service

`persist-service` 当前没有 HTTP 接口，职责是异步落库，不参与对外 API。

### 7.1 MQ 消费职责

| 事件类型 | 来源 | 写入目标 |
| --- | --- | --- |
| `OCR` | `question-core-service` 结果消费后再投递 | `q_question_ocr_task` |
| `OCR_LOCAL` | `ocr-service` | `q_ocr_task` |
| `AI` | `question-core-service` 结果消费后再投递 | `q_question_ai_task` |
| `AI_LOCAL` | `ocr-service` | 本地 AI 审计/历史表 |

说明：

- `persist-service` 只负责最终 MySQL 写回。
- Redis 热状态、WebSocket 推送、业务编排都不在 `persist-service` 内完成。

## 8. 外部依赖服务

### 8.1 export-sidecar

仓库中只有 Feign 客户端，没有本地控制器实现。

| 方法 | 路径 | 调用方 | 功能 |
| --- | --- | --- | --- |
| `POST` | `/internal/export/questions/word` | `exam-service` | 生成试卷 Word 文档 |

## 9. gaokao-corpus-service

### 9.1 对外业务接口 — 录入会话

基路径：`/api/gaokao/ingest-sessions`

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `POST` | `/api/gaokao/ingest-sessions` | 创建录入会话 |
| `GET` | `/api/gaokao/ingest-sessions` | 查询录入会话列表 |
| `POST` | `/api/gaokao/ingest-sessions/{sessionUuid}/files` | 上传 PDF / 图片 |
| `POST` | `/api/gaokao/ingest-sessions/{sessionUuid}/ocr-split` | 触发 OCR + 分题 |
| `GET` | `/api/gaokao/ingest-sessions/{sessionUuid}/draft-paper` | 取整卷草稿 |

### 9.2 对外业务接口 — 草稿编辑与发布

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `PUT` | `/api/gaokao/draft-papers/{draftPaperUuid}` | 保存整卷修正 |
| `PUT` | `/api/gaokao/draft-questions/{draftQuestionUuid}` | 保存单题修正 |
| `POST` | `/api/gaokao/draft-questions/{draftQuestionUuid}/analyze` | 对单题做 AI 分析 |
| `POST` | `/api/gaokao/draft-papers/{draftPaperUuid}/analyze` | 对整卷全部题做 AI 分析 |
| `POST` | `/api/gaokao/draft-questions/{draftQuestionUuid}/confirm` | 确认单题分析结果 |
| `POST` | `/api/gaokao/draft-papers/{draftPaperUuid}/publish` | 正式发布整卷到 gk_* 与 Qdrant |

### 9.3 对外业务接口 — 语料查询

基路径：`/api/gaokao/corpus`

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/gaokao/corpus/papers` | 查询正式高考试卷列表 |
| `GET` | `/api/gaokao/corpus/papers/{paperUuid}` | 查询试卷详情 |
| `GET` | `/api/gaokao/corpus/questions/{questionUuid}` | 查询正式题目详情 |

### 9.4 对外业务接口 — 拍照检索

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `POST` | `/api/gaokao/photo-query` | 单题拍照检索（不落库） |

### 9.5 对外业务接口 — 物化与标签

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `POST` | `/api/gaokao/materialize` | 物化高考题到 question-core-service |
| `GET` | `/api/gaokao/taxonomy` | 查询数学标签树 |

## 10. gaokao-analysis-service

### 10.1 内部接口

基路径：`/internal/gaokao-analysis`

| 方法 | 路径 | 调用方 | 功能 |
| --- | --- | --- | --- |
| `POST` | `/internal/gaokao-analysis/analyze-question` | `gaokao-corpus-service` | 对单题执行 AI 深分析 |
| `POST` | `/internal/gaokao-analysis/analyze-paper` | `gaokao-corpus-service` | 对整卷批量执行 AI 深分析 |
| `POST` | `/internal/gaokao-analysis/photo-query` | `gaokao-corpus-service` | 拍照题编排（OCR→清洗→分析→检索→重排→RAG） |

说明：`gaokao-analysis-service` 不直接对外暴露前端 API，所有对外入口都通过 `gaokao-corpus-service` 中转。
