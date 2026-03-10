# 后端完整功能调用链

更新时间：2026-03-09

## 说明

- 本文只写“真实已存在”的链路，不写理想状态。
- 异步链路中，MQ、Redis、WebSocket 都单独展开。
- 对外入口默认经 `gateway-service`，服务间调用默认走 Feign 或 RabbitMQ。

## 1. 登录与会话恢复

1. 前端调用 `POST /api/auth/login`。
2. `gateway-service` 将请求转发到 `auth-service /auth/login`。
3. `auth-service` 校验用户名密码，返回 JWT。
4. 前端后续请求携带 `Authorization: Bearer <token>`。
5. 网关/鉴权链路解析 token，将用户信息注入后续业务服务使用。
6. 前端可通过 `GET /api/auth/me` 校验当前会话是否有效。

## 2. 题库浏览与题目资源查看

### 2.1 分页浏览

1. `web` 端优先调用 `GET /api/questions/page?page={page}&size={size}`。
2. `question-core-service` 按页返回题目概览，包括：
   - 题干摘要
   - 主标签/副标签
   - 难度
   - 来源
   - 答案概览
3. `web` 可采用“后端每次 100 条、前端每页展示 20 条”的缓冲分页模式。
4. 旧接口 `GET /api/questions` 仍保留给兼容调用方，但不再适合作为大题库浏览主入口。

### 2.2 单题详情与资源

1. 用户打开单题详情时，前端调用 `GET /api/questions/{questionUuid}`。
2. `question-core-service` 组装该题的完整概览响应。
3. 若题干中需要渲染图片，前端再调用 `GET /api/questions/{questionUuid}/assets`。
4. `question-core-service` 优先从 Redis 读取 `question:assets:{questionUuid}` 短 TTL 缓存。
5. 若缓存未命中，则从 `q_question_asset` 查询当前题目的有效图片资源。

## 3. 修改题目来源

1. 前端展示来源下拉/输入框时，可先调用 `GET /api/questions/sources` 获取来源去重列表。
2. 用户提交来源后，前端调用 `PUT /api/questions/{questionUuid}/source`。
3. `question-core-service` 更新 `q_question.source`。
4. 题目摘要缓存被主动失效，后续列表/详情重新读取新值。

## 4. 试题篮与确认前组卷

### 4.1 试题篮切换

1. 前端调用 `POST /api/question-basket/{questionUuid}/toggle`。
2. `question-basket-service` 在 `q_question_basket` 中完成加入或移除。
3. 若当前用户已经存在确认前组卷状态，`question-basket-service` 同步更新 `q_basket_compose*`。
4. `question-basket-service` 同步更新试题篮 Redis 缓存：
   - `qforge:basket:uuids:{user}`
   - `qforge:basket:items:{user}`
5. 前端如需完整展示，再调用 `GET /api/question-basket` 获取条目概览。

### 4.2 进入组卷页

1. 前端调用 `GET /api/question-basket/compose`。
2. 若当前用户没有确认前组卷状态，`question-basket-service` 从 `q_question_basket` 初始化 `q_basket_compose*`。
3. `question-basket-service` 调用 `question-core-service /internal/questions/batch` 批量取题目摘要。
4. `question-basket-service` 返回确认前组卷结构给前端。

### 4.3 确认组卷并落库

1. 前端调用 `POST /api/question-basket/compose/confirm`。
2. `question-basket-service` 先做一次“试题篮 -> compose”一致性重放。
3. `question-basket-service` 调用 `exam-service /internal/exam-papers/from-basket-compose`。
4. `exam-service` 创建 `q_exam_paper / q_exam_section / q_exam_question`。
5. `question-basket-service` 清空 `q_question_basket` 与 `q_basket_compose*`。
6. 真实试卷此后不再受试题篮变化影响。

## 5. 试卷详情查询与保存

### 5.1 查看详情

1. 前端调用 `GET /api/exam-papers/{paperUuid}`。
2. `exam-service` 读取本地：
   - `ExamPaper`
   - `ExamSection`
   - `ExamQuestion`
3. `exam-service` 收集全部 `questionUuid`，调用 `question-core-service /internal/questions/batch`。
4. `question-core-service` 返回题目摘要。
5. `exam-service` 将摘要信息拼回试卷结构后返回前端。

### 5.2 保存整卷结构

1. 前端调用 `PUT /api/exam-papers/{paperUuid}/content`，提交 sections/questions 全量结构。
2. `exam-service` 先删除旧 section/question 记录。
3. `exam-service` 遍历新结构，收集全部 `questionUuid`。
4. `exam-service` 调用 `question-core-service /internal/questions/batch` 批量校验题目存在性与归属。
5. `exam-service` 跳过无效题目，写入新的 `ExamSection` 和 `ExamQuestion`。
6. `exam-service` 重算 `totalScore` 并返回新的试卷详情。

## 6. 导出试卷 Word

1. 前端调用 `POST /api/exam-papers/{paperUuid}/export/word`。
2. `exam-service` 读取试卷、section、question 结构。
3. `exam-service` 先调用 `question-core-service /internal/questions/batch-full` 批量获取完整题目数据。
4. `exam-service` 构造导出 payload，包含：
   - 试卷标题
   - section 顺序
   - 每个 section 的完整题目内容
   - 是否包含答案
5. `exam-service` 调用 `export-sidecar /internal/export/questions/word`。
6. `export-sidecar` 只负责模板渲染和 docx 生成，不负责业务查数。
7. `exam-service` 将二进制响应回传给前端下载。

## 7. 试卷解析任务创建到结果落地

### 7.1 创建任务

1. 前端调用 `POST /api/exam-parse/tasks`，上传 PDF/图片。
2. `exam-parse-service`：
   - 校验扩展名、数量
   - 创建 `ExamParseTask`
   - 保存原始文件到 `ExamParseSourceFile`
3. 事务提交后，`exam-parse-service` 发布 `ExamParseTaskCreatedEvent` 到 RabbitMQ。

### 7.2 OCR 与拆题

1. `ocr-service ExamParseTaskConsumer` 消费 `ExamParseTaskCreatedEvent`。
2. `ocr-service` 执行：
   - 多文件/多页 OCR 聚合
   - LLM 拆题
   - 题干/答案图片裁剪
   - stem/answer XML 生成
3. 每处理完一题，发布 `ExamParseQuestionResultEvent`。
4. 全部结束后，发布 `ExamParseCompletedEvent`。

### 7.3 暂存题写回与推送

1. `exam-parse-service ExamParseResultConsumer` 消费 `ExamParseQuestionResultEvent`。
2. 它将解析结果写入 `q_exam_parse_question`。
3. 它再通过 `WsPushService -> Redis pub/sub` 发送 `exam.parse.question.result` 事件。
4. `question-core-service` 的 Redis WS 监听器收到消息后，转发到当前用户 WebSocket session。
5. `ExamParseCompletedEvent` 到来后，`exam-parse-service` 更新任务状态/题量，再推送 `exam.parse.completed`。

## 8. 试卷解析确认入库

### 8.1 单题确认

1. 前端调用 `POST /api/exam-parse/tasks/{taskUuid}/questions/{seqNo}/confirm`。
2. `exam-parse-service` 校验该题属于当前用户且状态为 `PENDING`。
3. `exam-parse-service` 解析暂存的 `stemImagesJson`、`answerImagesJson`。
4. `exam-parse-service` 通过 Feign 调用 `question-core-service /internal/questions/from-parse`。
5. `question-core-service ParsedQuestionCreateService`：
   - 创建正式 `Question`
   - 创建正式 `Answer`
   - 保存题干/答案图片
   - 解析并应用 `mainTagsJson` / `secondaryTagsJson`
6. `question-core-service` 返回正式 `questionUuid`。
7. `exam-parse-service` 回写暂存题的 `questionUuid` 和 `confirmStatus=CONFIRMED`。

### 8.2 批量确认

1. 前端调用 `POST /api/exam-parse/tasks/{taskUuid}/confirm`。
2. `exam-parse-service` 读取该任务全部 `PENDING` 题。
3. 每题重复“单题确认”流程。
4. 某题失败则标记为 `SKIPPED` 并写错误信息，不中断其他题。

## 9. 单题 OCR 链路

### 9.1 创建 OCR 任务

1. 前端调用 `POST /api/questions/{questionUuid}/ocr-tasks`。
2. `question-core-service` 先做本地防冲突校验：
   - 答案 OCR 是否已有进行中的任务
   - Redis 防重锁是否已占用
3. `question-core-service` 通过 Feign 调用 `ocr-service /internal/ocr/tasks`。
4. `ocr-service` 创建本地 OCR 任务记录，并在事务提交后发布 `OcrTaskCreatedEvent`。
5. `question-core-service` 同时把任务热状态写入 Redis，并保存本地 `QuestionOcrTask`。

### 9.2 OCR 执行与业务回传

1. `ocr-service OcrTaskConsumer` 消费 `OcrTaskCreatedEvent`。
2. `ocr-service` 调用外部 GLM OCR，并按 `bizType` 转成：
   - stem XML
   - answer XML
3. `ocr-service` 发布两条消息：
   - `DbWriteBackEvent(OCR_LOCAL)` 给 `persist-service`
   - `OcrTaskResultEvent` 给 `question-core-service`
4. `question-core-service OcrResultConsumer` 消费结果后：
   - 更新 Redis 热状态
   - 释放答案 OCR 防重锁
   - 处理裁剪图片
   - 发布 `DbWriteBackEvent(OCR)` 给 `persist-service`
   - 通过 Redis pub/sub -> WebSocket 推送 `ocr.task.succeeded` 或 `ocr.task.failed`

## 10. AI 分析链路

### 10.1 发起任务

1. 前端调用 `POST /api/questions/{questionUuid}/ai-analysis`。
2. `question-core-service` 校验题干和答案存在。
3. `question-core-service` 生成 `taskUuid`，先写 Redis 热状态。
4. `question-core-service` 保存 `QuestionAiTask(status=PENDING)`。
5. `question-core-service` 发布 `AiAnalysisTaskCreatedEvent` 到 RabbitMQ。

### 10.2 AI 执行与结果回传

1. `ocr-service AiAnalysisTaskConsumer` 消费 `AiAnalysisTaskCreatedEvent`。
2. `ocr-service` 调用智谱模型做标签/难度分析。
3. `ocr-service` 发布：
   - `DbWriteBackEvent(AI_LOCAL)` 给 `persist-service`
   - `AiAnalysisResultEvent` 给 `question-core-service`
4. `question-core-service AiAnalysisResultConsumer` 消费后：
   - 更新 Redis 热状态
   - 再投递 `DbWriteBackEvent(AI)` 给 `persist-service`
   - 通过 WebSocket 推送 `ai.analysis.succeeded` 或 `ai.analysis.failed`

### 10.3 应用推荐

1. 前端调用 `PUT /api/questions/{questionUuid}/ai-tasks/{taskUuid}/apply`。
2. `question-core-service` 优先从 Redis 读取任务状态，Redis 失效后回退 DB。
3. 只有 `SUCCESS` 任务允许应用。
4. 若请求中包含 `tags`，则走统一标签替换逻辑。
5. 若请求中包含 `difficulty`，则更新题目难度。
6. AI 任务状态改为 `APPLIED`。

## 11. Redis 与 WebSocket 转发链

1. 业务服务本身并不一定持有当前用户的 WS 连接。
2. `exam-parse-service` 和 `question-core-service` 都会把待推送事件发布到 Redis channel `qforge:ws:push`。
3. `question-core-service RedisWsEventListener` 订阅该 channel。
4. 监听器将事件转交给 `QuestionWsHandler`。
5. `QuestionWsHandler` 根据 `user` 将消息发送到该用户当前所有 WS session。

## 12. persist-service 的位置

1. `persist-service` 不负责业务编排。
2. 它只消费 `DbWriteBackEvent` 做最终数据库写回。
3. 因此业务上的“成功/失败可见性”优先依赖 Redis 热状态和 WebSocket 推送，而不是等 MySQL 最终一致完成。
