# Gaokao Review Remediation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 修复本轮代码审阅中已经被代码事实验证的高优先级问题，并把误报、设计缺口和后续修复方案从实现任务中剥离出来。

**Architecture:** 本轮只修复不会改变既有业务边界的缺陷，优先保证索引回调一致性、Qdrant 写入稳定性、XML 安全性、文件处理内存占用和 MQ 失败兜底。对需要额外业务决策的问题，只做事实记录和后续方案，不在本轮强行改行为。

**Tech Stack:** Java 17, Spring Boot 3.5, Spring AMQP, Spring RestClient, MyBatis-Plus, JUnit 5, Mockito。

---

## Facts

- 已确认需要修复：
  - `InternalCorpusController.updatePaperIndex()` 删除 + 重建 + 状态更新不在事务内。
  - `DraftServiceImpl.buildStemXml()` 直接把 OCR 文本塞入 CDATA，`]]>` 会破坏 XML。
  - `GaokaoPaperIndexRequestedConsumer` 的 token 估算对中文明显失真。
  - `IngestServiceImpl.calculateSha256()` 与 `encodeFileAsBase64()` 使用 `Files.readAllBytes(...)`。
  - `VectorServiceImpl.ensureCollection()` 存在并发创建竞态，且点写入后立即搜索没有显式等待索引完成。
  - `gaokao-analysis-service` 的 Rabbit 拓扑没有 DLQ。
- 已复核为误报或当前设计事实，不在本轮修：
  - `MaterializationServiceImpl` 读取的是正式表 `GkQuestionProfile.knowledgePathJson`，字段名与表结构一致，不是 `knowledgeTagsJson`。
  - `GaokaoQuestionCreateService` 将 XML 写入 `stemText` 与当前 `question-service` 既有存储约定一致；`ParsedQuestionCreateService` 和 `QuestionCommandServiceImpl` 也是同样模式。
  - `subjectCode = "MATH"` 与当前“高考数学专项”范围一致，不是本轮缺陷。
  - `RestClient` 当前通过 Spring 默认 `RestClient.Builder` 构建，未持有自建可关闭连接池对象，本轮不把“未关闭”当作已证实 bug。
- 已确认存在，但需要额外设计决策，先记录方案，不在本轮直接改：
  - 发布前状态校验：现有代码没有把草稿状态推进到 `READY_TO_PUBLISH` 的独立流程，直接加校验会让发布功能整体失效。
  - OCR Base64 大小限制：需要决定统一阈值和配置落点，避免误伤现有 PDF / 图片流程。
  - OCR DTO 收敛到 `internal-api-contract`、Feign 重试/熔断、Prompt 注入防护等属于下一轮治理项。

## Task 1: 固化最小回归测试

**Files:**
- Create: `backend/services/gaokao-corpus-service/src/test/java/io/github/kamill7779/qforge/gaokaocorpus/service/DraftServiceImplTest.java`
- Create: `backend/services/gaokao-corpus-service/src/test/java/io/github/kamill7779/qforge/gaokaocorpus/service/IngestServiceImplTest.java`
- Create: `backend/services/gaokao-corpus-service/src/test/java/io/github/kamill7779/qforge/gaokaocorpus/controller/InternalCorpusControllerTest.java`
- Create: `backend/services/gaokao-analysis-service/src/test/java/io/github/kamill7779/qforge/gaokaoanalysis/service/impl/VectorServiceImplTest.java`
- Create: `backend/services/gaokao-analysis-service/src/test/java/io/github/kamill7779/qforge/gaokaoanalysis/mq/GaokaoPaperIndexRequestedConsumerTest.java`
- Create: `backend/services/gaokao-analysis-service/src/test/java/io/github/kamill7779/qforge/gaokaoanalysis/config/RabbitTopologyConfigTest.java`

**Step 1: 写失败测试**

- `DraftServiceImplTest`
  - 断言 OCR 文本包含 `]]>` 时，生成的 `<stem>` XML 仍然可解析，且原始文本内容完整保留。
- `IngestServiceImplTest`
  - 断言 checksum 正确。
  - 断言 Base64 编码结果正确，且不依赖 `Files.readAllBytes(...)` 的旧实现细节。
- `InternalCorpusControllerTest`
  - 断言 `updatePaperIndex` 带 `@Transactional`。
- `VectorServiceImplTest`
  - 断言 collection 创建时若遇到“已存在”响应，不把其当成失败。
  - 断言点 upsert 请求带 `wait=true`。
- `GaokaoPaperIndexRequestedConsumerTest`
  - 断言中文文本 token 估算不再退化到 `length/4`。
- `RabbitTopologyConfigTest`
  - 断言主队列绑定了 DLQ 相关参数。

**Step 2: 运行失败测试验证 RED**

Run:

```bash
mvn -pl services/gaokao-corpus-service,services/gaokao-analysis-service -am test -Dtest=DraftServiceImplTest,IngestServiceImplTest,InternalCorpusControllerTest,VectorServiceImplTest,GaokaoPaperIndexRequestedConsumerTest,RabbitTopologyConfigTest
```

Expected:

- 新测试先失败，失败原因分别对应事务缺失、CDATA 破坏、Qdrant 请求未等待、DLQ 未配置、旧 token 估算错误等真实问题。

## Task 2: 修复 corpus 侧一致性与安全问题

**Files:**
- Modify: `backend/services/gaokao-corpus-service/src/main/java/io/github/kamill7779/qforge/gaokaocorpus/controller/InternalCorpusController.java`
- Modify: `backend/services/gaokao-corpus-service/src/main/java/io/github/kamill7779/qforge/gaokaocorpus/service/DraftServiceImpl.java`
- Modify: `backend/services/gaokao-corpus-service/src/main/java/io/github/kamill7779/qforge/gaokaocorpus/service/IngestServiceImpl.java`

**Step 1: 最小实现**

- 给 `updatePaperIndex` 加事务，确保删旧索引记录、写新索引记录、更新试卷状态是一个原子操作。
- 让 `buildStemXml` 对 `]]>` 做安全拆分，保持文本语义不变且 XML 有效。
- 将 SHA-256 改为流式 digest。
- 将 Base64 编码改为流式读取 + Base64 encoder 输出，避免把整文件原始字节再复制一份到内存。

**Step 2: 运行目标测试**

Run:

```bash
mvn -pl services/gaokao-corpus-service -am test -Dtest=DraftServiceImplTest,IngestServiceImplTest,InternalCorpusControllerTest
```

Expected:

- corpus 侧新增测试全部通过。

## Task 3: 修复 analysis 侧索引稳定性

**Files:**
- Modify: `backend/services/gaokao-analysis-service/src/main/java/io/github/kamill7779/qforge/gaokaoanalysis/service/impl/VectorServiceImpl.java`
- Modify: `backend/services/gaokao-analysis-service/src/main/java/io/github/kamill7779/qforge/gaokaoanalysis/mq/GaokaoPaperIndexRequestedConsumer.java`
- Modify: `backend/services/gaokao-analysis-service/src/main/java/io/github/kamill7779/qforge/gaokaoanalysis/config/RabbitTopologyConfig.java`

**Step 1: 最小实现**

- 把 Qdrant collection 创建改成幂等式处理：直接尝试创建，遇到“已存在/冲突”按成功处理。
- 点 upsert 时显式带 `wait=true`，降低“刚写入就搜索”拿不到结果的概率。
- 调整 token 估算，至少对中文按 code point 级别计算，避免 `length/4` 明显低估。
- 为 paper-index 队列增加 DLQ 和 dead-letter routing key。

**Step 2: 运行目标测试**

Run:

```bash
mvn -pl services/gaokao-analysis-service -am test -Dtest=VectorServiceImplTest,GaokaoPaperIndexRequestedConsumerTest,RabbitTopologyConfigTest
```

Expected:

- analysis 侧新增测试全部通过。

## Task 4: 文档与问题单同步

**Files:**
- Modify: `prompt.md`
- Modify: `problem.md`

**Step 1: 更新事实**

- 在 `prompt.md` 增加“当前审阅校准事实”，明确哪些审阅结论不能直接照搬。
- 在 `problem.md` 把问题拆成：
  - 已确认并已修复
  - 已确认但暂缓
  - 复核后不采纳

**Step 2: 写清后续方案**

- 对暂缓项写明：
  - 为什么本轮不直接改
  - 推荐修法
  - 依赖的业务决策或额外改造

## Task 5: 最终验证

**Files:**
- Modify: none

**Step 1: 运行本轮完整目标测试**

Run:

```bash
mvn -pl services/gaokao-corpus-service,services/gaokao-analysis-service -am test
```

Expected:

- 目标模块测试通过。
- 若存在与本轮无关的历史失败，需在交付说明和 `problem.md` 中单列。

