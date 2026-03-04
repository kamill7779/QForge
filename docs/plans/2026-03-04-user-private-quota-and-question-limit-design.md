# User Private Quota + VIP Entitlement Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在 `question-service` 中建立可持续扩展的“用户私有题库配额系统”，同时支持免费额度、VIP 订阅扩容、一次性加购扩容（充值包）、手工调账，并覆盖题目数量与图片容量两类限制。

**Architecture:** 采用“使用量（Usage）与权益（Entitlement）分离”的配额引擎。写入路径只改动使用量，VIP/充值只改动权益；系统实时计算并维护“有效上限快照（Effective Limit）”，写库时按快照做原子校验。通过事务 + 行级锁 + 幂等键保证并发与消息重试场景下不超卖。

**Tech Stack:** Spring Boot, MyBatis-Plus, MySQL, Redis, RabbitMQ, JUnit5/Mockito

---

## 1. 为什么你指出的问题是对的

你提到“后续做充值/VIP 扩容会不行”，这个判断成立。  
如果只用固定配置（`qforge.business.max-*`）限制：

- 无法按“用户维度”差异化额度（普通用户 2GB、VIP 20GB）。
- 无法表示“时间有效期”（月度订阅到期、活动包过期）。
- 无法审计充值来源与历史（订单、退款、人工补偿）。
- 无法在降级后给出有状态治理（超额用户该怎么收敛）。

所以必须把“配额能力”从静态配置升级为“权益模型”。

## 2. 设计目标（扩展性优先）

- 支持两类资源维度：
  - 私有题目数量 `private_question_count`
  - 私有图片容量 `private_asset_bytes`
- 支持多来源叠加额度：
  - 免费档位（base）
  - VIP 订阅（periodic）
  - 充值加购包（one-time/permanent or time-limited）
  - 手工运营调账（manual adjust）
- 支持权益生命周期：
  - 生效、到期、撤销、退款
- 支持降级/到期后“超额用户”治理：
  - 禁止新增占用，允许删减/公开化回收
- 兼容现有业务路径：
  - `updateStem/syncInlineImages`
  - `updateAnswer/syncAnswerImages`
  - `OcrResultConsumer.saveExtractedImages`
  - `createDraft/deleteDraftQuestion`
  - 新增 `visibility` 切换

## 3. 方案对比（2+1）

### 方案 A：继续“全局配置限额”

- 优点：实现最简单。
- 缺点：无法支持 VIP/充值用户级差异，不满足目标。
- 结论：淘汰。

### 方案 B：在 `question-service` 内实现“配额引擎 + 权益表”（推荐）

- 优点：可快速落地，改动边界清晰，和现有写路径最匹配。
- 优点：未来可平滑抽离为独立 quota-service（事件契约不变）。
- 缺点：配额逻辑在 question-service 内，后期需注意服务职责演进。
- 结论：当前阶段最佳。

### 方案 C：直接上独立 `quota-service`

- 优点：领域隔离好，长期架构更优。
- 缺点：当前项目会引入跨服务强一致/分布式事务复杂度。
- 结论：作为后续演进方向，不作为本轮首发方案。

## 4. 领域模型（关键）

### 4.1 使用量表：`q_user_quota_usage`

```sql
CREATE TABLE IF NOT EXISTS q_user_quota_usage (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user VARCHAR(128) NOT NULL UNIQUE,
    private_question_count INT NOT NULL DEFAULT 0,
    private_asset_bytes BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

- 只记录“当前已占用”。
- 只在题目/图片业务变更时更新。

### 4.2 权益表：`q_user_quota_entitlement`

```sql
CREATE TABLE IF NOT EXISTS q_user_quota_entitlement (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    entitlement_uuid CHAR(36) NOT NULL UNIQUE,
    owner_user VARCHAR(128) NOT NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'FREE_PLAN/VIP_SUBSCRIPTION/RECHARGE_PACK/MANUAL_ADJUST',
    source_ref VARCHAR(128) NULL COMMENT '订单号、订阅号、工单号',
    question_quota_delta INT NOT NULL DEFAULT 0,
    asset_bytes_quota_delta BIGINT NOT NULL DEFAULT 0,
    valid_from DATETIME NOT NULL,
    valid_to DATETIME NULL COMMENT 'NULL=永久',
    status VARCHAR(32) NOT NULL COMMENT 'PENDING/ACTIVE/EXPIRED/REVOKED',
    idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quota_entitlement_idempotency (idempotency_key),
    INDEX idx_quota_entitlement_owner_active (owner_user, status, valid_from, valid_to)
);
```

- 只记录“可用额度来源”。
- VIP/充值/退款都映射为 entitlement 的增减或状态流转。

### 4.3 有效上限快照表：`q_user_quota_profile`

```sql
CREATE TABLE IF NOT EXISTS q_user_quota_profile (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    owner_user VARCHAR(128) NOT NULL UNIQUE,
    effective_question_limit INT NOT NULL DEFAULT 0,
    effective_asset_bytes_limit BIGINT NOT NULL DEFAULT 0,
    quota_state VARCHAR(32) NOT NULL DEFAULT 'NORMAL' COMMENT 'NORMAL/OVER_LIMIT/FROZEN',
    source_version BIGINT NOT NULL DEFAULT 0,
    recomputed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

- 写路径只读该表做快速判定，不每次聚合 entitlement。
- 由权益变更事件触发重算。

### 4.4 审计流水表：`q_user_quota_ledger`

```sql
CREATE TABLE IF NOT EXISTS q_user_quota_ledger (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    ledger_uuid CHAR(36) NOT NULL UNIQUE,
    owner_user VARCHAR(128) NOT NULL,
    biz_type VARCHAR(64) NOT NULL COMMENT 'CREATE_DRAFT/UPDATE_STEM/UPDATE_ANSWER/OCR_SAVE/VISIBILITY_CHANGE/DELETE_DRAFT',
    biz_ref VARCHAR(128) NOT NULL COMMENT 'questionUuid/answerUuid/taskUuid',
    question_delta INT NOT NULL,
    asset_bytes_delta BIGINT NOT NULL,
    question_after INT NOT NULL,
    asset_bytes_after BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_quota_ledger_idempotency (idempotency_key),
    INDEX idx_quota_ledger_owner_time (owner_user, created_at)
);
```

- 用于审计、追账、排障。
- 同时作为业务幂等保障点之一。

### 4.5 资产表增强（必须）

```sql
ALTER TABLE q_question_asset
    ADD COLUMN image_size_bytes INT NOT NULL DEFAULT 0 COMMENT 'base64 解码后字节数';
```

- 不再在每次校验时通过字符串长度估算。
- 统一用持久化精确值做 delta。

## 5. 核心算法与事务边界

### 5.1 统一入口方法

建议新增：

- `QuotaCommandService.checkAndApplyUsageDelta(...)`
- 入参：`ownerUser, questionDelta, assetBytesDelta, bizType, bizRef, idempotencyKey`

### 5.2 原子流程（单事务）

1. `SELECT ... FOR UPDATE` 锁定 `q_user_quota_usage` 行。  
2. 读取 `q_user_quota_profile`（必要时也锁行，统一加锁顺序避免死锁）。  
3. 计算 `newUsage = oldUsage + delta`。  
4. 若 delta 是增加且 `newUsage > effectiveLimit`，抛业务异常。  
5. 更新 usage。  
6. 写 ledger（幂等键去重）。  
7. 提交事务。  

### 5.3 幂等策略（关键）

- 对 MQ 入口（OCR）必须使用稳定 `idempotencyKey`，如：  
  - `ocr:{taskUuid}:ref:{refKey}:hash:{imgHash}`
- 对 HTTP 写路径可使用：
  - `question:{questionUuid}:op:{operationType}:rev:{clientRevision}`
  - 或服务端按“前后快照差值”天然幂等（推荐结合）。

## 6. 业务场景映射（完整覆盖）

### 场景 A：创建题目（默认 PRIVATE）

- `questionDelta=+1, assetDelta=0`
- 先校验配额再落题目。

### 场景 B：题干/答案图片同步

- 先算本次变更 delta：
  - 新增图：`+size`
  - 替换图：`+newSize-oldSize`
  - 删除图：`-oldSize`
- 题目是 `PRIVATE` 才记账；`PUBLIC` 不计入私有 usage。

### 场景 C：可见性切换（新增接口）

- `PRIVATE -> PUBLIC`：`questionDelta=-1`，`assetDelta=-questionTotalAssetBytes`
- `PUBLIC -> PRIVATE`：反向增量，需先校验配额。

### 场景 D：删除私有草稿

- 回收本题占用：`-1` 与 `-assetBytes`。

### 场景 E：VIP 到期/降级导致超额

- 不删除数据，不强制改可见性。
- 将 `quota_state` 标记 `OVER_LIMIT`：
  - 禁止任何正向增长操作（新增私有题/新增私有图/PUBLIC->PRIVATE）
  - 允许下降操作（删题、删图、PRIVATE->PUBLIC）
- 用户回到限额内后自动恢复 `NORMAL`。

## 7. VIP/充值集成设计

## 7.1 事件驱动接口（推荐）

由支付/会员系统投递：

- `UserEntitlementGrantedEvent`
- `UserEntitlementRevokedEvent`
- `UserEntitlementExpiredEvent`

字段至少包含：

- `eventId`（全局幂等）
- `ownerUser`
- `sourceType/sourceRef`
- `questionQuotaDelta`
- `assetBytesQuotaDelta`
- `validFrom/validTo`

`question-service` 消费后：

1. Upsert entitlement（按 eventId 幂等）。
2. 重算 profile。
3. 若用户超额，标记 `OVER_LIMIT` 并发通知。

## 7.2 非事件模式（短期兜底）

可先提供内部管理接口：

- `POST /internal/quota/entitlements/grant`
- `POST /internal/quota/entitlements/revoke`

后续替换为消息消费，无需改核心配额引擎。

## 8. AOP 是否使用（最终建议）

- **核心结论**：AOP 可用，但不能作为配额核心。
- AOP 适用：
  - 记录每次 quota check/apply 的耗时、delta、结果。
  - 发现“资产写入但未记账”的异常路径（告警）。
- AOP 不适用：
  - 最终额度判断与扣减（必须在事务业务服务中显式执行）。

## 9. API 设计补充

### 9.1 用户可见接口

- `GET /api/questions/quota`
  - 返回 usage、effective limit、quota_state、使用率。
- `PUT /api/questions/{questionUuid}/visibility`
  - body: `{ "visibility": "PRIVATE" | "PUBLIC" }`

### 9.2 异常码

- `USER_PRIVATE_QUESTION_QUOTA_EXCEEDED`
- `USER_PRIVATE_ASSET_QUOTA_EXCEEDED`
- `USER_QUOTA_OVER_LIMIT_GROWTH_FORBIDDEN`
- `QUESTION_VISIBILITY_CHANGE_NOT_ALLOWED`

## 10. 配置模型（从全局限额升级）

在 `qforge.business` 中保留“默认免费额度”：

- `free-question-limit`
- `free-asset-bytes-limit`

注意：这些只作为“基础权益”，最终上限 = 基础权益 + entitlement 叠加。

## 11. 数据迁移与上线步骤（缜密版）

1. 新增四张配额表 + `image_size_bytes` 列（灰度发布，先不启用拦截）。  
2. 回填 `q_question_asset.image_size_bytes`。  
3. 生成初始 usage（按现存 PRIVATE 题目与图片聚合）。  
4. 为每个用户创建基础 entitlement（FREE_PLAN）。  
5. 重算 profile。  
6. 启用写路径 quota check。  
7. 打开监控告警（超限拒绝率、OVER_LIMIT 用户数）。  
8. 接入 VIP/充值事件源。  

## 12. 测试策略（必须覆盖）

### 12.1 单元测试

- `QuotaCalculatorTest`：权益叠加、到期、撤销、负向调账。
- `QuotaCommandServiceTest`：正负 delta、超限拒绝、幂等重试。

### 12.2 服务层测试

- `createDraft` 在不同档位下的限额行为。
- `updateStem/updateAnswer/OCR` 的 delta 正确性。
- `visibility` 切换回收/占用逻辑。
- `OVER_LIMIT` 状态下增长操作禁止、缩减操作允许。

### 12.3 并发测试

- 同一用户并发创建题目 N 次，不出现超卖。
- 同一用户并发上传图片，不出现 usage 与实际不一致。

### 12.4 对账测试

- 提供重建任务：`rebuildUsage(ownerUser)` 与全量 `rebuildAllUsage()`。
- 断言重建后 usage 与 profile 与线上一致。

## 13. 实施任务（按工程可执行拆分）

### Task 1: Schema 与实体层

**Files:**
- Modify: `backend/sql/init-schema.sql`
- Create: `.../entity/UserQuotaUsage.java`
- Create: `.../entity/UserQuotaEntitlement.java`
- Create: `.../entity/UserQuotaProfile.java`
- Create: `.../entity/UserQuotaLedger.java`
- Modify: `.../entity/QuestionAsset.java`

**Step 1: 写失败测试（Repository 层映射）**

- 验证新表 CRUD 与索引字段映射。

**Step 2: 实现最小实体与 Repository**

- 增加 `for update` 查询方法。

**Step 3: 运行测试**

- `mvn -pl backend/services/question-service test -Dtest=*Repository*`

### Task 2: 配额引擎服务

**Files:**
- Create: `.../service/quota/QuotaCalculator.java`
- Create: `.../service/quota/QuotaCommandService.java`
- Create: `.../service/quota/QuotaQueryService.java`
- Modify: `.../config/QForgeBusinessProperties.java`

**Step 1: 先写 QuotaCalculator 测试**

- 覆盖 FREE + VIP + PACK 叠加。

**Step 2: 实现计算与原子 apply**

- 完成 check+apply+ledger 幂等。

**Step 3: 运行测试**

- `mvn -pl backend/services/question-service test -Dtest=*Quota*`

### Task 3: 接入现有写路径

**Files:**
- Modify: `.../service/QuestionCommandService.java`
- Modify: `.../service/QuestionCommandServiceImpl.java`
- Modify: `.../mq/OcrResultConsumer.java`
- Modify: `.../repository/QuestionAssetRepository.java`

**Step 1: 先写行为测试**

- 每个入口一条“增长成功”与“一条超限失败”。

**Step 2: 改造 create/update/delete/visibility**

- 所有 delta 经 `QuotaCommandService`。

**Step 3: 运行测试**

- `mvn -pl backend/services/question-service test`

### Task 4: VIP/充值权益接入

**Files:**
- Create: `.../mq/QuotaEntitlementConsumer.java`
- Create: `.../dto/internal/GrantEntitlementRequest.java`
- Create: `.../controller/InternalQuotaController.java`（如短期需要）

**Step 1: 先写幂等消费测试**

- 重复事件不重复加额度。

**Step 2: 实现 entitlement upsert + profile 重算**

- 到期/撤销逻辑进入同一重算入口。

**Step 3: 运行测试**

- `mvn -pl backend/services/question-service test -Dtest=*Entitlement*`

### Task 5: 对账与运维可观测

**Files:**
- Create: `.../service/quota/QuotaReconcileService.java`
- Modify: `docs/question-service-api.md`

**Step 1: 增加 rebuild 任务与告警指标**

- 暴露 `overLimitUserCount`、`quotaRejectRate`。

**Step 2: 文档补齐**

- 用户配额 API、内部权益 API、错误码。

## 14. 风险控制与回滚

- 风险：新配额逻辑拦截太严导致存量流程失败。  
  - 方案：开关化 `quota.enforcement.enabled=false` 可降级到只记账不拦截。  
- 风险：权益事件延迟导致用户刚充值仍被拒。  
  - 方案：同步 grant API 兜底 + 消费重试 + 幂等。  
- 风险：历史数据与 usage 偏差。  
  - 方案：每日离线对账 + 自动修复 + 告警。  

---

这个版本已经覆盖你关心的“VIP/充值扩容”核心缺陷，且保留后续抽离独立 quota-service 的演进路径。  
如果你同意，我下一步可以按这个文档直接开始实现 Task 1（Schema + 实体 + Repository），并给你第一轮可运行测试结果。
