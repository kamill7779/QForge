# Gaokao 审阅问题复核与修复跟踪

更新时间：2026-03-10

对应计划：

- `plan/2026-03-10-10-gaokao-review-remediation-plan.md`

## 1. 本轮结论

本轮没有照单全收审阅摘要，而是先按当前代码事实复核后再修。

- 已确认并已修复：6 项
- 已复核后不采纳：4 项
- 已确认但本轮暂缓：11 项

本轮修复遵循“高优先级、低范围扩散、可回归测试”的策略，没有去动需要额外业务决策的项。

## 2. 已确认并已修复

### 2.1 索引回调事务缺失

- 问题：`InternalCorpusController.updatePaperIndex()` 删除旧索引记录、写入新记录、更新试卷状态不在一个事务内。
- 修复：为该方法补上 `@Transactional`，确保索引回调原子提交。
- 文件：
  - `backend/services/gaokao-corpus-service/src/main/java/io/github/kamill7779/qforge/gaokaocorpus/controller/InternalCorpusController.java`
  - `backend/services/gaokao-corpus-service/src/test/java/io/github/kamill7779/qforge/gaokaocorpus/controller/InternalCorpusControllerTest.java`

### 2.2 Draft XML 的 `]]>` 注入风险

- 问题：`DraftServiceImpl.buildStemXml()` 直接把 OCR 文本包进 CDATA，文本里出现 `]]>` 会生成非法 XML。
- 修复：对 CDATA 结束标记做安全拆分，保持文本内容不变且 XML 可解析。
- 文件：
  - `backend/services/gaokao-corpus-service/src/main/java/io/github/kamill7779/qforge/gaokaocorpus/service/DraftServiceImpl.java`
  - `backend/services/gaokao-corpus-service/src/test/java/io/github/kamill7779/qforge/gaokaocorpus/service/DraftServiceImplTest.java`

### 2.3 Qdrant collection 创建竞态

- 问题：`VectorServiceImpl.ensureCollection()` 原实现是 `GET -> PUT`，属于典型 check-then-act。
- 修复：改成进程内幂等缓存 + 直接 `PUT` 创建；若远端返回 `409` 视为“已存在”并按成功处理，不再走预读。
- 文件：
  - `backend/services/gaokao-analysis-service/src/main/java/io/github/kamill7779/qforge/gaokaoanalysis/service/impl/VectorServiceImpl.java`
  - `backend/services/gaokao-analysis-service/src/test/java/io/github/kamill7779/qforge/gaokaoanalysis/service/impl/VectorServiceImplTest.java`

### 2.4 向量写入后立即搜索的一致性问题

- 问题：Qdrant 点写入后立即搜索，没有显式等待索引完成。
- 修复：点 upsert 请求显式加 `wait=true`，降低“刚写完搜不到”的概率。
- 文件：
  - `backend/services/gaokao-analysis-service/src/main/java/io/github/kamill7779/qforge/gaokaoanalysis/service/impl/VectorServiceImpl.java`
  - `backend/services/gaokao-analysis-service/src/test/java/io/github/kamill7779/qforge/gaokaoanalysis/service/impl/VectorServiceImplTest.java`

### 2.5 中文 token 估算严重低估

- 问题：`estimateTokens()` 使用 `length/4`，对中文 chunk 明显失真。
- 修复：改成 Unicode 感知估算，CJK 字符按单字符记 token，拉丁字符按 run 长度折算。
- 文件：
  - `backend/services/gaokao-analysis-service/src/main/java/io/github/kamill7779/qforge/gaokaoanalysis/mq/GaokaoPaperIndexRequestedConsumer.java`
  - `backend/services/gaokao-analysis-service/src/test/java/io/github/kamill7779/qforge/gaokaoanalysis/mq/GaokaoPaperIndexRequestedConsumerTest.java`

### 2.6 RabbitMQ 缺少 DLQ

- 问题：索引消费主队列没有死信路由，失败消息没有兜底去向。
- 修复：为 `PAPER_INDEX_REQUESTED_QUEUE` 增加 dead-letter exchange / routing key，并声明对应 DLQ 与 binding。
- 文件：
  - `backend/libs/common-contract/src/main/java/io/github/kamill7779/qforge/common/contract/GaokaoIndexingConstants.java`
  - `backend/services/gaokao-analysis-service/src/main/java/io/github/kamill7779/qforge/gaokaoanalysis/config/RabbitTopologyConfig.java`
  - `backend/services/gaokao-analysis-service/src/test/java/io/github/kamill7779/qforge/gaokaoanalysis/config/RabbitTopologyConfigTest.java`

### 2.7 Rabbit 拓扑修复后的启动回归

- 问题：新增 DLQ 后，`RabbitTopologyConfig` 同时存在两个 `Queue` Bean，但 `gaokaoPaperIndexRequestedBinding(...)` 仍按裸类型注入，导致 `gaokao-analysis-service` 在 Docker 启动时出现 `NoUniqueBeanDefinitionException`。此外，RabbitMQ 已存在的旧队列没有 DLQ 参数，新的拓扑声明会触发 `PRECONDITION_FAILED`。
- 修复：
  - 给主绑定方法参数显式加 `@Qualifier("gaokaoPaperIndexRequestedQueue")`，消除 Spring 容器装配歧义。
  - 本地 Docker 验证时删除旧的 `qforge.gaokao.paper.index.requested.q` 队列并重启 `gaokao-analysis-service`，让 broker 以新参数重新声明主队列和 DLQ。
- 评审关注点：
  - 这不是新的业务设计，而是 Rabbit 拓扑变更的配套交付要求。
  - 生产或共享环境上线时，需要显式执行队列迁移；否则服务虽然能启动，但监听容器会因队列参数不一致反复报 `PRECONDITION_FAILED`。
- 文件：
  - `backend/services/gaokao-analysis-service/src/main/java/io/github/kamill7779/qforge/gaokaoanalysis/config/RabbitTopologyConfig.java`
  - `backend/services/gaokao-analysis-service/src/test/java/io/github/kamill7779/qforge/gaokaoanalysis/config/RabbitTopologyConfigTest.java`

## 3. 已复核后不采纳

### 3.1 `MaterializationServiceImpl` 字段名错误

- 审阅结论：`getKnowledgePathJson()` 应改成 `getKnowledgeTagsJson()`。
- 复核结果：不采纳。
- 原因：`MaterializationServiceImpl` 读取的是正式表实体 `GkQuestionProfile`，字段本来就叫 `knowledgePathJson`，对应数据库列 `knowledge_path_json`。草稿预览层才是 `knowledgeTagsJson`。

### 3.2 `GaokaoQuestionCreateService` XML/Text 混淆

- 审阅结论：`stemXml` 不应写入 `stemText`。
- 复核结果：不采纳。
- 原因：当前 `question-service` 的既有存储约定就是把可渲染题干 XML 存到 `Question.stemText`。`ParsedQuestionCreateService`、`QuestionCommandServiceImpl` 都是同样模式，这不是本次回归引入的单点错误。

### 3.3 `VectorServiceImpl RestClient` 资源泄漏

- 审阅结论：`RestClient` 未关闭会导致连接池耗尽。
- 复核结果：本轮不采纳为已证实 bug。
- 原因：当前实现通过 Spring 默认 `RestClient.Builder` 构建，没有引入自管理的 Apache/OkHttp 连接池对象，也没有出现必须手动 `close()` 的资源句柄。现阶段更明确的真实问题是“collection 创建竞态”和“upsert 未等待”，已按根因修复。

### 3.4 `subjectCode = "MATH"` 硬编码

- 审阅结论：非数学科目无法发布。
- 复核结果：不采纳。
- 原因：当前子系统本来就是“高考数学专项”，`subjectCode = MATH` 符合既定范围，不是本轮缺陷。若未来扩成多学科，再整体设计学科枚举、配置和前端输入。

## 4. 已确认但本轮暂缓

以下问题都值得修，但不适合在本轮直接硬改。每项都给出推荐修法，供后续评审。

### 4.1 `IngestServiceImpl` 全文件读入内存

- 现状：`calculateSha256()` 和 `encodeFileAsBase64()` 仍使用 `Files.readAllBytes(...)`。
- 本轮状态：暂缓。
- 暂缓原因：需要补一个能真实约束“大文件内存峰值”的回归验证，当前只改实现不容易形成可靠断言。
- 推荐修法：
  - `calculateSha256()` 改为 `DigestInputStream` 流式计算。
  - Base64 编码改为 `Base64.Encoder.wrap(OutputStream)` 流式输出。
  - 追加大文件压测或内存基线验证，而不是只做功能断言。

### 4.2 OCR Base64 大小限制缺失

- 现状：内部 OCR 同步接口只有 `@NotBlank`，没有大小上限。
- 本轮状态：暂缓。
- 暂缓原因：阈值需要结合当前 PDF / 图片链路定，直接写死容易误伤现网导入。
- 推荐修法：
  - 在 `ocr-service` 请求 DTO 上加 `@Size(max = ...)`。
  - 阈值放入 `backend/configs/ocr-service.yml` 或 `gaokao-corpus-service.yml`。
  - `gaokao-corpus-service` 在调用前也做文件大小前置校验，避免把超大文件先转成 Base64 再失败。

### 4.3 发布前无状态校验

- 现状：`PublishServiceImpl.publishPaper()` 只检查草稿是否存在，不检查是否达到“可发布”状态。
- 本轮状态：暂缓。
- 暂缓原因：当前代码里没有完整的 `READY_TO_PUBLISH` 推进路径；如果现在直接加校验，会把现有发布入口整体打断。
- 推荐修法：
  - 明确草稿状态机：`EDITING -> ANALYZING -> READY_TO_PUBLISH -> PUBLISHED`。
  - 把“确认分析结果 / 完整性校验通过”作为进入 `READY_TO_PUBLISH` 的唯一入口。
  - 然后再在 `publishPaper()` 中强制校验状态。

### 4.4 OCR DTO 未收敛到 `internal-api-contract`

- 现状：`ocr-service` 与 `gaokao-corpus-service` 各维护一份 `OcrRecognizeRequest`。
- 本轮状态：暂缓。
- 推荐修法：
  - 抽到 `backend/libs/internal-api-contract`。
  - 两侧 Feign/Controller 共用一份 DTO。
  - 顺手补上校验注解与字段文档。

### 4.5 `InternalQuestionController` 新端点缺少有效校验

- 现状：`/internal/questions/from-gaokao` 只有 `@RequestBody`，请求对象也没有 Bean Validation 约束。
- 本轮状态：暂缓。
- 推荐修法：
  - 给 `CreateQuestionFromGaokaoRequest` 增加 `@NotBlank` / `@NotNull`。
  - 控制器入参改成 `@Valid @RequestBody`。
  - 增加非法 payload 的 controller 测试。

### 4.6 AI 分析 JSON 反序列化异常被吞掉

- 现状：`AiAnalysisServiceImpl.requestJsonAnalysis()` 失败时直接回退默认结果，只打一条 warn。
- 本轮状态：暂缓。
- 推荐修法：
  - 区分“模型返回空结果”和“返回非 JSON”。
  - 把原始响应截断后落 debug 日志或审计表。
  - 为错误类型增加 metric，便于观察模型稳定性。

### 4.7 Prompt 注入风险

- 现状：用户输入直接拼进 LLM prompt。
- 本轮状态：暂缓。
- 推荐修法：
  - 明确 system / user 边界，不把原始输入混到结构指令段。
  - 对用户文本做定界包裹，例如 XML/JSON fenced block。
  - 对“必须输出 JSON”的约束引入 schema 校验，而不是只靠 prompt。

### 4.8 `searchSimilar()` 的 filters 未使用

- 现状：接口签名接受 filters，但查询体没带 filter。
- 本轮状态：暂缓。
- 推荐修法：
  - 明确需要支持的 metadata 过滤字段，例如 `questionTypeCode`、`difficultyLevel`、`provinceCode`。
  - 在 Qdrant query body 中映射成 filter 条件。
  - 增加“有 filter / 无 filter”两套搜索测试。

### 4.9 Feign 调用无重试 / 熔断

- 现状：`gaokao-analysis-service` 和 `gaokao-corpus-service` 的内部调用缺少统一容错配置。
- 本轮状态：暂缓。
- 推荐修法：
  - 统一引入 OpenFeign + Resilience4j 配置。
  - 对回调类接口区分重试与不可重试错误。
  - 明确超时、最大重试次数和 fallback 记录方式。

### 4.10 `MaterializationServiceImpl.parseJsonTokens()` 手工解析 JSON

- 现状：通过 split 处理 JSON 字符串，容错性差。
- 本轮状态：暂缓。
- 推荐修法：
  - 用 `ObjectMapper.readValue(rawJson, new TypeReference<List<String>>() {})`。
  - 对异常 JSON 明确记录 warning，而不是半解析。

### 4.11 发布链路的完整性校验缺失

- 现状：发布前没有统一验证“题干、答案、结构、确认预览”是否完整。
- 本轮状态：暂缓。
- 推荐修法：
  - 在 publish 入口前增加单独的 `DraftPublishReadinessService`。
  - 输出结构化错误列表给前端，而不是在发布过程中边复制边失败。

## 5. 验证记录

### 5.1 RED

已先跑失败测试并确认抓到真实问题：

- `InternalCorpusControllerTest`
- `DraftServiceImplTest`
- `VectorServiceImplTest`
- `GaokaoPaperIndexRequestedConsumerTest`
- `RabbitTopologyConfigTest`

### 5.2 GREEN

已执行：

```bash
mvn -pl services/gaokao-corpus-service,services/gaokao-analysis-service -am test
```

结果：

- `common-contract`：3 tests, 0 failures
- `gaokao-corpus-service`：2 tests, 0 failures
- `gaokao-analysis-service`：4 tests, 0 failures
- 命令退出码：0

补充运行态验证：

```bash
docker compose build gaokao-corpus-service gaokao-analysis-service
docker compose up -d gaokao-corpus-service gaokao-analysis-service
docker exec qforge-rabbitmq rabbitmqctl delete_queue qforge.gaokao.paper.index.requested.q
docker compose restart gaokao-analysis-service
docker exec qforge-rabbitmq rabbitmqctl list_queues name arguments
```

结果：
- `gaokao-corpus-service`、`gaokao-analysis-service` 均成功启动并保持 `Up`。
- `qforge.gaokao.paper.index.requested.q` 已按新拓扑声明为带 `x-dead-letter-exchange` / `x-dead-letter-routing-key` 的主队列。
- `qforge.gaokao.paper.index.requested.dlq` 已成功声明。

说明：

- 本轮验证覆盖的是相关模块与其依赖 reactor，不是整个 `backend/` 全仓测试。
