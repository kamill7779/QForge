# User Private Quota And Question Limit Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 `question-service` 中新增“用户私有题库配额”能力，包含私有图片总容量限制与私有题目数量限制；当题目切换为公开时，不再占用私有配额。

**Architecture:** 采用“服务层显式记账 + 行级锁原子更新 + 可选 AOP 审计”的方案。配额状态持久化到独立汇总表，所有会影响配额的写路径在同一事务内计算 delta 并更新，避免仅靠查询聚合导致并发穿透。

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL, Redis, RabbitMQ, JUnit5/Mockito

---

## 1. 当前代码现状（已核对）

- 题目默认私有：`createDraft` 中固定 `visibility=PRIVATE`（`QuestionCommandServiceImpl` 125-136）。
- 图片写库路径至少有 3 条：
  - `updateStem -> syncInlineImages`（142-216）
  - `updateAnswer -> syncAnswerImages`（229-506）
  - `OcrResultConsumer.saveExtractedImages`（133-214）
- 当前仅有“单题图片张数/单图大小”限制，配置在 `qforge.business.max-inline-images/max-image-binary-bytes`（`application.yml` 48-53，`QForgeBusinessProperties` 17-43）。
- 没有“用户级别总量/总数”数据结构，也没有“切换公开/私有”接口（`QuestionController` 43-182）。
- DB 中 `q_question` 已有 `visibility` 字段（`init-schema.sql` 17-30），`q_question_asset` 目前只有 `image_data`，没有独立 `image_size_bytes`（76-91）。

## 2. 需求澄清（按你的描述落地）

- 新增用户私有题库两类配额：
  - 私有图片总量（bytes）
  - 私有题目数量（count）
- 用户将题目设为公开后，该题及其图片不再计入私有配额。
- 后续可基于该配额做硬限制，防止数据膨胀。

## 3. 可选方案对比（含 AOP）

### 方案 A：纯 AOP 拦截 `QuestionAssetRepository.save/delete*`

- 优点：侵入业务代码少，看起来集中。
- 问题：
  - 需要在切面里反查 `questionId -> owner/visibility`，逻辑耦合重。
  - MyBatis Mapper + default method + 多入口（MQ Consumer）下，拦截点稳定性差。
  - 很难天然处理“同事务回滚/并发超卖/visibility 变更”的一致性。
- 结论：不推荐作为主方案。

### 方案 B：服务层显式记账（推荐）

- 做法：在所有“会改动配额”的应用服务流程中显式计算 delta，并在同事务更新配额汇总行。
- 优点：
  - 语义清晰，可直接拿到 owner/visibility/业务上下文。
  - 易于做并发控制（`SELECT ... FOR UPDATE`）。
  - 测试边界清晰，回归风险可控。
- 成本：需要改造所有写路径（但路径已明确，工作量可控）。

### 方案 C：数据库 Trigger 自动记账

- 优点：数据库层一致性强。
- 问题：可维护性与可测试性差，迁移/排障成本高，不符合当前仓库风格。
- 结论：不推荐。

## 4. 推荐落地：B 主 + A 辅（可选）

### 4.1 回答你的 AOP 问题

- 可以用 Spring AOP，但建议“用于审计与防漏”，不建议“承担核心记账逻辑”。
- 核心配额更新放在服务层事务内；AOP 仅做：
  - 写路径埋点（before/after delta、耗时）
  - 断言类检查（例如方法退出后验证已调用配额服务）

### 4.2 新增数据模型

#### 表 1：`q_user_private_quota_usage`（新增）

```sql
CREATE TABLE IF NOT EXISTS q_user_private_quota_usage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user VARCHAR(128) NOT NULL UNIQUE,
    private_question_count INT NOT NULL DEFAULT 0,
    private_asset_bytes BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_quota_owner (owner_user)
);
```

#### 表 2：`q_question_asset` 增强（新增列）

```sql
ALTER TABLE q_question_asset
    ADD COLUMN image_size_bytes INT NOT NULL DEFAULT 0 COMMENT 'base64 解码后字节数';
```

- 理由：避免每次聚合都实时解析 base64 长度。
- 写入时直接保存精确字节数，delta 计算稳定。

### 4.3 配置扩展（支持热更新）

在 `qforge.business` 下新增：

- `max-private-question-count`（默认如 `5000`）
- `max-private-asset-bytes`（默认如 `2147483648` = 2GB）

说明：沿用现有 `QForgeBusinessProperties` 模式，Nacos 热更新即生效。

## 5. 关键业务流程设计

### 流程 A：创建题目（默认 PRIVATE）

- 入口：`createDraft`
- 事务内：
  - 锁定 `owner_user` 配额行（不存在则初始化）。
  - 校验 `private_question_count + 1 <= max-private-question-count`。
  - 通过后创建题目并增量记账 `question +1`。

### 流程 B：题目/答案图片写库

- 入口：`syncInlineImages`、`syncAnswerImages`、`OcrResultConsumer.saveExtractedImages`
- 统一算法：
  - 计算 `deltaBytes = sum(new image_size_bytes) - sum(replaced/deleted image_size_bytes)`。
  - 若题目 `visibility=PRIVATE`：
    - 先校验 `private_asset_bytes + deltaBytes <= max-private-asset-bytes`。
    - 再更新资产并更新配额汇总。
  - 若 `PUBLIC`：仅写资产，不动私有配额。

### 流程 C：题目切换公开/私有（新增）

- 新增接口：`PUT /api/questions/{questionUuid}/visibility`
- `PRIVATE -> PUBLIC`：
  - 读取该题有效图片总字节 `questionAssetBytes`。
  - 配额汇总做 `question -1, assetBytes -questionAssetBytes`。
- `PUBLIC -> PRIVATE`：
  - 先校验加回后不超限，再更新 `question +1, assetBytes +questionAssetBytes`。
- 建议规则：仅 `READY` 题目允许公开，防止草稿公开。

### 流程 D：删除草稿

- 入口：`deleteDraftQuestion`
- 若题目当前是 `PRIVATE`：
  - 扣减该题图片总字节与题目数 `-1`。
- 然后执行现有删逻辑（题目、资源、任务）。

## 6. 接口与错误码扩展

### 6.1 新增接口

- `PUT /api/questions/{questionUuid}/visibility`
  - Request: `{ "visibility": "PRIVATE" | "PUBLIC" }`
  - Response: `QuestionStatusResponse`（可扩展返回 visibility）
- `GET /api/questions/quota`
  - 返回当前用户私有配额使用量与上限。

### 6.2 新增错误码（422）

- `USER_PRIVATE_QUESTION_QUOTA_EXCEEDED`
- `USER_PRIVATE_ASSET_QUOTA_EXCEEDED`
- `QUESTION_VISIBILITY_CHANGE_NOT_ALLOWED`

## 7. 代码结构扩展建议

### 7.1 新增组件

- `entity/UserPrivateQuotaUsage.java`
- `repository/UserPrivateQuotaUsageRepository.java`
- `service/UserQuotaService.java`
- `service/UserQuotaServiceImpl.java`
- `dto/UpdateVisibilityRequest.java`
- `dto/UserQuotaResponse.java`

### 7.2 需改造现有文件

- `backend/sql/init-schema.sql`
- `question/config/QForgeBusinessProperties.java`
- `question/repository/QuestionRepository.java`
- `question/repository/QuestionAssetRepository.java`
- `question/service/QuestionCommandService.java`
- `question/service/QuestionCommandServiceImpl.java`
- `question/controller/QuestionController.java`
- `question/mq/OcrResultConsumer.java`
- `docs/question-service-api.md`

### 7.3 可选 AOP（辅助）

- `question/aop/QuotaMutationAuditAspect.java`
  - 仅做审计日志、链路指标，不做配额最终判定与扣减。

## 8. 并发与一致性策略

- 每个 `owner_user` 的配额更新都在同一行上加锁（`SELECT ... FOR UPDATE`）。
- 校验与更新必须同事务内完成，避免并发超卖。
- 对 MQ 消费路径（`OcrResultConsumer`）增加事务边界，确保“资产写库 + 配额更新”原子化。
- 提供每日离线对账任务（rebuild），用于修正极端异常下的偏差。

## 9. 测试设计

### 单元测试

- `UserQuotaServiceTest`
  - 正向增减
  - 超限拒绝
  - 并发场景（模拟锁/版本）

### 服务层测试

- `QuestionCommandServiceImpl` 增量测试：
  - `createDraft` 达上限时失败
  - `updateStem/updateAnswer` 增图、删图、替换图对配额 delta 正确
  - `visibility` 切换增减正确
  - `deleteDraftQuestion` 回收正确

### API 测试

- `QuestionApiRestStandardTest` 新增：
  - visibility 切换成功/失败路径
  - quota 查询返回结构

## 10. 分阶段实施计划

### Task 1: 数据层与配置

**Files:**
- Modify: `backend/sql/init-schema.sql`
- Modify: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/config/QForgeBusinessProperties.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/entity/UserPrivateQuotaUsage.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/repository/UserPrivateQuotaUsageRepository.java`

**Steps:**
1. 新增配额汇总表与 `image_size_bytes` 列。
2. 增加配额上限配置项与 getter/setter。
3. 完成实体与 repository 基础读写方法（含 `for update` 查询）。

### Task 2: 配额服务

**Files:**
- Create: `.../service/UserQuotaService.java`
- Create: `.../service/UserQuotaServiceImpl.java`

**Steps:**
1. 实现 `checkAndApplyDelta(ownerUser, questionDelta, assetBytesDelta)`。
2. 实现 `getUsage(ownerUser)`。
3. 统一抛出业务异常码（超限场景）。

### Task 3: 命令服务接入

**Files:**
- Modify: `.../service/QuestionCommandService.java`
- Modify: `.../service/QuestionCommandServiceImpl.java`
- Modify: `.../repository/QuestionAssetRepository.java`
- Modify: `.../repository/QuestionRepository.java`

**Steps:**
1. 在 `createDraft` 增加题目数配额校验与记账。
2. 在图片同步逻辑中引入 `deltaBytes` 计算与记账。
3. 在删除草稿路径增加配额回收。
4. 新增 visibility 变更服务方法。

### Task 4: 控制器与 DTO

**Files:**
- Modify: `.../controller/QuestionController.java`
- Create: `.../dto/UpdateVisibilityRequest.java`
- Create: `.../dto/UserQuotaResponse.java`

**Steps:**
1. 新增 `PUT /{questionUuid}/visibility`。
2. 新增 `GET /quota`。
3. 补充参数校验与错误映射。

### Task 5: MQ 写库路径接入

**Files:**
- Modify: `.../mq/OcrResultConsumer.java`

**Steps:**
1. 在 extracted images 保存时同时更新配额 delta。
2. 为该流程补充事务边界与错误日志。

### Task 6: 测试与文档

**Files:**
- Modify/Create: `src/test/java/...` 对应测试类
- Modify: `docs/question-service-api.md`

**Steps:**
1. 补齐单元/集成/API 测试。
2. 更新 API 文档与错误码表。
3. 增加一份运维对账说明（如何重建配额汇总）。

## 11. 风险与回滚

- 风险 1：历史数据无 `image_size_bytes`。
  - 处理：首轮上线跑一次 backfill SQL，按 `image_data` 估算并回填。
- 风险 2：漏掉某条写路径导致配额漂移。
  - 处理：每日离线重建 + AOP 审计报警。
- 风险 3：并发创建题目导致超限穿透。
  - 处理：严格行级锁 + 同事务校验更新。

---

如果你确认这个设计，我下一步可以直接按这个文档开始实现第一阶段（数据层 + 配额服务），并在每个阶段给你可验证的测试结果。
