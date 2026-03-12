# 后端服务接口总览

更新时间：2026-03-12

## 说明

- 本文基于当前仓库中的控制器、Feign 客户端、应用服务和 MQ 消费链路整理。
- 对外访问统一以 `gateway-service` 暴露的网关路径为准；服务间调用单独标注为“内部接口”。
- 大多数业务控制器通过 `X-Auth-User` 识别当前用户，实际值通常由认证链路注入。
- `question-service` 在代码目录中仍叫 `question-service`，但运行时/配置层实际已经作为 `question-core-service` 使用，本文统一按 `question-core-service` 记述。

## 1. gateway-service

### 1.1 自身接口

| 方法 | 对外路径 | 功能 |
| --- | --- | --- |
| `GET` | `/public/ping` | 公共健康检查 |
| `GET` | `/gateway/ping` | 网关健康检查 |

### 1.2 路由映射

| 网关路径 | 目标服务 | 备注 |
| --- | --- | --- |
| `/api/auth/**` | `auth-service` | 使用 `StripPrefix=1` |
| `/api/exam-parse/**` | `exam-parse-service` | 路径不改写 |
| `/api/exam-papers/**` | `exam-service` | 路径不改写 |
| `/api/question-types/**` | `exam-service` | 路径不改写 |
| `/api/question-basket/**` | `question-basket-service` | 路径不改写 |
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
| `GET` | `/api/questions` | 查询当前用户题目总表 | 兼容保留，全量返回 |
| `GET` | `/api/questions/page?page={page}&size={size}` | 分页查询当前用户题目概览 | 当前 `web` 主分页入口 |
| `GET` | `/api/questions/{questionUuid}` | 查询单题详情 | 轻量详情接口 |
| `GET` | `/api/questions/{questionUuid}/assets` | 获取题干图片资源 | 先查 Redis，再回退数据库 |
| `POST` | `/api/questions` | 创建题目草稿 | 可选初始题干 |
| `PUT` | `/api/questions/{questionUuid}/stem` | 更新题干 XML 和题干图片 | 会同步 `q_question_asset` |
| `POST` | `/api/questions/{questionUuid}/complete` | 将题目标记为 `READY` | 需要题干和至少一条答案 |
| `POST` | `/api/questions/{questionUuid}/answers` | 新增答案 | 支持答案图片 |
| `PUT` | `/api/questions/{questionUuid}/answers/{answerUuid}` | 修改答案 | 支持答案图片同步 |
| `DELETE` | `/api/questions/{questionUuid}/answers/{answerUuid}` | 删除答案 | 禁止删到最后一条 |
| `POST` | `/api/questions/{questionUuid}/ocr-tasks` | 提交 OCR 任务 | `bizType` 为 `QUESTION_STEM` 或 `ANSWER_CONTENT` |
| `DELETE` | `/api/questions/{questionUuid}` | 删除单题 | 级联删除相关数据 |
| `POST` | `/api/questions/batch-delete` | 批量删题 | 请求体字段 `questionUuids` |
| `PUT` | `/api/questions/{questionUuid}/tags` | 更新标签 | 统一走标签分配服务 |
| `PUT` | `/api/questions/{questionUuid}/difficulty` | 更新难度值 | 失效题目摘要缓存 |
| `PUT` | `/api/questions/{questionUuid}/source` | 更新来源 | 失效题目摘要缓存 |
| `GET` | `/api/questions/sources` | 查询当前用户来源去重列表 | 仓储层去重查询 |
| `POST` | `/api/questions/{questionUuid}/ai-analysis` | 发起 AI 标签/难度分析 | 异步任务 |
| `GET` | `/api/questions/{questionUuid}/ai-tasks` | 查询 AI 任务列表 | 展示历史分析结果 |
| `PUT` | `/api/questions/{questionUuid}/ai-tasks/{taskUuid}/apply` | 应用 AI 推荐 | 回写标签和难度 |

### 3.2 标签接口

基路径：`/api/tags`

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/tags` | 获取主标签目录与副标签分类元信息 |

### 3.3 内部接口

基路径：`/internal/questions`

| 方法 | 路径 | 调用方 | 功能 |
| --- | --- | --- | --- |
| `GET` | `/internal/questions/batch` | `exam-service` | 批量取题目摘要 |
| `POST` | `/internal/questions/batch-full` | `exam-service` | 批量取题目完整数据 |
| `GET` | `/internal/questions/{questionUuid}/exists` | `exam-service` | 校验题目是否存在且归属指定用户 |
| `POST` | `/internal/questions/from-parse` | `exam-parse-service` | 将解析暂存题落为正式题目 |

### 3.4 WebSocket

| 协议 | 路径 | 功能 |
| --- | --- | --- |
| `WS` | `/ws/questions?user={username}&token={token}` | OCR、AI、试卷解析等异步事件推送 |

## 4. question-basket-service

基路径：`/api/question-basket`

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/question-basket` | 查询试题篮完整条目 |
| `GET` | `/api/question-basket/uuids` | 查询试题篮题目 UUID 列表 |
| `POST` | `/api/question-basket/{questionUuid}` | 加入试题篮 |
| `POST` | `/api/question-basket/{questionUuid}/toggle` | 在试题篮中切换 |
| `DELETE` | `/api/question-basket/{questionUuid}` | 单条移除 |
| `DELETE` | `/api/question-basket` | 清空试题篮 |
| `GET` | `/api/question-basket/compose` | 获取确认前组卷状态 |
| `PUT` | `/api/question-basket/compose/meta` | 保存确认前组卷元信息 |
| `PUT` | `/api/question-basket/compose/content` | 保存确认前组卷结构 |
| `POST` | `/api/question-basket/compose/confirm` | 确认组卷并生成真实试卷 |

## 5. exam-service

### 5.1 题型配置

基路径：`/api/question-types`

| 方法 | 路径 | 功能 |
| --- | --- | --- |
| `GET` | `/api/question-types` | 查询系统题型和用户自定义题型 |
| `POST` | `/api/question-types` | 新增自定义题型 |
| `PUT` | `/api/question-types/{id}` | 修改自定义题型 |
| `DELETE` | `/api/question-types/{id}` | 删除自定义题型 |

### 5.2 试卷

基路径：`/api/exam-papers`

| 方法 | 路径 | 功能 | 关键说明 |
| --- | --- | --- | --- |
| `GET` | `/api/exam-papers` | 查询当前用户试卷列表 | 已做批量聚合 |
| `POST` | `/api/exam-papers` | 新建空试卷 | 默认时长来自配置 |
| `GET` | `/api/exam-papers/{paperUuid}` | 查询试卷详情 | 内部批量调用题目摘要接口 |
| `PUT` | `/api/exam-papers/{paperUuid}` | 更新试卷元信息 | 标题、副标题、说明、时长、状态 |
| `DELETE` | `/api/exam-papers/{paperUuid}` | 删除试卷 | 逻辑删除整卷实体 |
| `PUT` | `/api/exam-papers/{paperUuid}/content` | 原子保存整卷结构 | 先删旧 section/question，再写新结构 |
| `POST` | `/api/exam-papers/{paperUuid}/export/word` | 导出 Word | 通过 `export-sidecar` 执行导出 |

### 5.3 内部接口

基路径：`/internal/exam-papers`

| 方法 | 路径 | 调用方 | 功能 |
| --- | --- | --- | --- |
| `POST` | `/internal/exam-papers/from-basket-compose` | `question-basket-service` | 将确认前组卷状态落为真实试卷 |

## 6. exam-parse-service

基路径：`/api/exam-parse`

| 方法 | 路径 | 功能 | 关键说明 |
| --- | --- | --- | --- |
| `POST` | `/api/exam-parse/tasks` | 创建试卷解析任务 | multipart 上传 |
| `GET` | `/api/exam-parse/tasks` | 查询当前用户解析任务列表 | 返回任务状态、进度、题量等 |
| `GET` | `/api/exam-parse/tasks/{taskUuid}` | 查询任务详情 | 返回 `task` + `questions` |
| `PUT` | `/api/exam-parse/tasks/{taskUuid}/questions/{seqNo}` | 修改解析题 | 可改题干、答案、题型、标签、难度 |
| `POST` | `/api/exam-parse/tasks/{taskUuid}/confirm` | 批量确认入库 | 仅处理 `PENDING` 题 |
| `POST` | `/api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/confirm` | 单题确认入库 | 落正式题后回写 `questionUuid` |
| `POST` | `/api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/skip` | 跳过题目 | `PENDING -> SKIPPED` |
| `POST` | `/api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/unskip` | 恢复题目 | `SKIPPED -> PENDING` |
| `DELETE` | `/api/exam-parse/tasks/{taskUuid}` | 删除任务 | 当前改为取消态和后续清理 |

## 7. ocr-service

`ocr-service` 不直接服务前端页面，主要承担内部任务入口和 MQ 消费。

### 7.1 内部 HTTP 接口

基路径：`/internal/ocr/tasks`

| 方法 | 路径 | 调用方 | 功能 |
| --- | --- | --- | --- |
| `POST` | `/internal/ocr/tasks` | `question-core-service` | 创建 OCR 任务并返回 `taskUuid` |

### 7.2 关键 MQ 消费职责

| 消费者 | 输入 | 输出 |
| --- | --- | --- |
| `OcrTaskConsumer` | `OcrTaskCreatedEvent` | `OcrTaskResultEvent` + `DbWriteBackEvent(OCR_LOCAL)` |
| `AiAnalysisTaskConsumer` | `AiAnalysisTaskCreatedEvent` | `AiAnalysisResultEvent` + `DbWriteBackEvent(AI_LOCAL)` |
| `ExamParseTaskConsumer` | `ExamParseTaskCreatedEvent` | 逐题 `ExamParseQuestionResultEvent` + `ExamParseCompletedEvent` |

## 8. persist-service

`persist-service` 当前没有 HTTP 接口，职责是异步落库。

| 事件类型 | 来源 | 写入目标 |
| --- | --- | --- |
| `OCR` | `question-core-service` 结果消费后再投递 | `q_question_ocr_task` |
| `OCR_LOCAL` | `ocr-service` | `q_ocr_task` |
| `AI` | `question-core-service` 结果消费后再投递 | `q_question_ai_task` |
| `AI_LOCAL` | `ocr-service` | 本地 AI 审计/历史表 |

## 9. export-sidecar

仓库中只有 Feign 客户端，没有本地控制器实现。

| 方法 | 路径 | 调用方 | 功能 |
| --- | --- | --- | --- |
| `POST` | `/internal/export/questions/word` | `exam-service` | 生成试卷 Word 文档 |
