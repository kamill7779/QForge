# Answer OCR Asset Pipeline Implementation Plan (V2: Sequential Task Model)

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在不改动 `q_question_ocr_task` / `q_ocr_task` 结构的前提下，补齐答案 OCR 的“裁图-渲染-存储”链路，新增 `q_answer_asset` 作为答案配图正式存储，并通过 Redis + WS 支撑录题阶段实时预览。

**Architecture:** 采用“顺序任务约束”模型：同一题目同一时刻仅允许一个进行中的 `ANSWER_CONTENT` OCR 任务。OCR 中间产物（答案裁图）先存 Redis（短 TTL），用户确认/保存答案时再落 `q_answer_asset`。`q_question_asset` 只保留题干图片，答案图片彻底解耦。

**Tech Stack:** Java 17, Spring Boot 3.5, MyBatis-Plus, RabbitMQ, Redis, MySQL 8.4, Electron renderer JS, Maven, node:test.

---

## 0. 设计更正说明（针对你提出的问题）

你指出的问题成立：

```sql
ALTER TABLE q_question_ocr_task ... answer_uuid/answer_draft_uuid
ALTER TABLE q_ocr_task ... answer_uuid/answer_draft_uuid
```

这两组改动不应放在当前方案里。原因：

1. 当前产品流程是“题干确认后进入答案录入”，答案 OCR 在前端已按阶段顺序执行（有 `ansOcrBusy` 禁用）。
2. 现有通用 OCR 任务表本质是任务流水，不应承载答案领域语义字段。
3. 真正需要的不是“给 OCR 表加字段”，而是：
   - 增加答案正式资源表 `q_answer_asset`
   - 增加顺序约束（后端硬约束，防止并发乱序）
   - 增加 OCR 中间裁图的暂存与消费机制（Redis）

结论：**删除旧方案中对 `q_question_ocr_task` / `q_ocr_task` 的答案字段扩展**。

---

## 1. 现状证据（代码/文档）

1. 前端已对答案 OCR 按阶段与状态做顺序限制：`PENDING_ANSWER` 且 `ansOcrBusy` 时按钮禁用（`frontend/src/renderer.js`）。
2. 后端 `submitOcrTask` 当前只区分 `bizType`，没有“同题仅一个进行中答案 OCR”硬约束（`question-service`）。
3. OCR API 文档仅定义 `bizType + imageBase64`，没有答案上下文字段（`docs/question-service-api.md`）。

---

## 2. 新目标架构（不改 OCR 任务表）

### 2.1 核心规则

1. `q_question_ocr_task`、`q_ocr_task` 保持通用，不新增答案字段。
2. 新增后端校验：同一 `questionUuid` 下，`ANSWER_CONTENT` 只能存在一个 `PENDING/PROCESSING` 任务。
3. 答案 OCR 裁图为“中间态”：先存 Redis（短 TTL）。
4. 用户最终保存答案时，才将答案配图写入 `q_answer_asset`（正式态）。

### 2.2 数据流

1. 前端提交答案 OCR（`bizType=ANSWER_CONTENT`）。
2. ocr-service 对答案也执行：`preprocess -> bbox crop -> answer xml convert`。
3. question-service 收到成功结果后：
   - 将 extracted images 存入 Redis：`ocr:answer:assets:{taskUuid}`，TTL 30 分钟
   - 将 `fig-N` 重写为稳定答案临时引用：`aocr-{taskUuid}-img-N`
   - WS 推送 `recognizedText`（已重写）
4. 前端收到 WS 后，调用新接口拉取该 task 的答案临时图片并渲染。
5. 用户点击“添加答案 / 保存答案”时，前端提交 `inlineImages`，后端落库到 `q_answer_asset`。
6. Redis 中间态到期自动清理；可选在答案保存成功后主动删除该 task 的 Redis key。

---

## 3. 数据模型设计

### 3.1 新增正式答案资源表 `q_answer_asset`

```sql
CREATE TABLE IF NOT EXISTS q_answer_asset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    asset_uuid CHAR(36) NOT NULL UNIQUE,
    question_id BIGINT NOT NULL,
    answer_uuid CHAR(36) NOT NULL,
    ref_key VARCHAR(64) NOT NULL COMMENT '答案 XML 引用 key，如 a<answerUuid8>-img-1',
    image_data MEDIUMTEXT NOT NULL,
    mime_type VARCHAR(128) NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'MANUAL / OCR_IMPORT',
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_q_ans_asset_question (question_id),
    INDEX idx_q_ans_asset_answer (answer_uuid),
    INDEX idx_q_ans_asset_ref (answer_uuid, ref_key),
    CONSTRAINT fk_q_ans_asset_question FOREIGN KEY (question_id) REFERENCES q_question(id)
);
```

### 3.2 明确不改动

1. `q_question_ocr_task` 不加 `answer_uuid/answer_draft_uuid`
2. `q_ocr_task` 不加 `answer_uuid/answer_draft_uuid`

---

## 4. 接口与契约设计

### 4.1 OCR 提交接口（保持兼容）

`POST /api/questions/{questionUuid}/ocr-tasks`

```json
{
  "bizType": "ANSWER_CONTENT",
  "imageBase64": "..."
}
```

不新增答案字段，保持现有契约。

### 4.2 新增：按 OCR task 拉取答案临时配图

`GET /api/questions/{questionUuid}/ocr-tasks/{taskUuid}/answer-assets`

```json
[
  {
    "refKey": "aocr-<taskUuid>-img-1",
    "imageData": "...",
    "mimeType": "image/png"
  }
]
```

- 数据源：Redis（key: `ocr:answer:assets:{taskUuid}`）
- 只用于录题阶段临时渲染

### 4.3 现有答案保存接口

`POST /api/questions/{questionUuid}/answers` 与 `PUT /api/questions/{questionUuid}/answers/{answerUuid}` 保持不变，继续通过 `inlineImages` 提交最终配图。

---

## 5. 后端改造点

### 5.1 ocr-service

1. 新增 `AnswerXmlConverter`。
2. 修改 `OcrTaskConsumer`：`ANSWER_CONTENT` 走与题干一致的 `bbox` 裁图流程。
3. `OcrTaskResultEvent` 继续复用 `extractedImagesJson`，无需改事件结构。

### 5.2 question-service

1. `submitOcrTask` 增加顺序硬校验：
   - 查询 `questionUuid + bizType=ANSWER_CONTENT + status in (PENDING, PROCESSING)`
   - 存在则返回 `409 OCR_TASK_CONFLICT`
2. `OcrResultConsumer`：
   - `ANSWER_CONTENT` 成功时将裁图写 Redis 临时区
   - 重写 `recognizedText` 中图片 ref
   - WS 推送重写后的文本
3. 新增临时答案资产查询接口与 service。
4. 新增 `AnswerAsset` 实体与 repository。
5. `validateAndSyncAnswerImages/syncAnswerImages` 从 `q_question_asset` 迁移到 `q_answer_asset`。
6. `listAssets` 返回时合并：
   - 题干资产：`q_question_asset`
   - 答案正式资产：`q_answer_asset`

### 5.3 persist-service

无需新增字段适配（因为 OCR 任务表不改）。仅保持现有 OCR 状态落库逻辑。

---

## 6. 前端改造点

1. WS 收到 `ANSWER_CONTENT` 成功后：
   - 先更新 `answerDraft`（保持现有行为）
   - 再调用新接口拉取该 task 的答案临时配图并写入 `entry.answerImages`
2. `resolveAnswerImage` 优先读取 `entry.answerImages`，其次兼容旧 `inlineImages`。
3. 添加答案时从 `answerImages` 组装 `inlineImages` 提交。
4. 题库编辑页继续复用同一答案图片数据结构，避免双轨。

---

## 7. 文档与容器

1. `docs/question-service-api.md`：新增临时答案资产查询接口；补充顺序冲突错误码。
2. `backend/README.md`：补充答案 OCR 临时资产 Redis 机制说明。
3. `backend/sql/init-schema.sql`：新增 `q_answer_asset`。`question-service` 迁移新增 V4 脚本确保可执行升级。
4. `docker-compose*.yml`：无需因本次重构新增服务；仅确认 mysql init script 覆盖到开发模式。

---

## 8. 实施任务（可执行）

### Task A: 顺序约束（后端硬校验）

**Files:**
- Modify: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/repository/QuestionOcrTaskRepository.java`
- Modify: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/service/QuestionCommandServiceImpl.java`
- Create: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/OcrTaskSequentialConstraintTest.java`

**验证命令：**
`mvn -pl services/question-service -Dtest=OcrTaskSequentialConstraintTest test`

### Task B: 新增 `q_answer_asset` 与实体仓储

**Files:**
- Modify: `backend/sql/init-schema.sql`
- Create: `backend/services/question-service/src/main/resources/db/migration/V4__answer_asset_and_answer_ocr_xml.sql`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/entity/AnswerAsset.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/repository/AnswerAssetRepository.java`

**验证命令：**
`rg -n "q_answer_asset" backend/sql/init-schema.sql backend/services/question-service/src/main/resources/db/migration`

### Task C: 答案 OCR 裁图链路补齐（ocr-service）

**Files:**
- Create: `backend/services/ocr-service/src/main/java/io/github/kamill7779/qforge/ocr/client/AnswerXmlConverter.java`
- Modify: `backend/services/ocr-service/src/main/java/io/github/kamill7779/qforge/ocr/mq/OcrTaskConsumer.java`
- Create: `backend/services/ocr-service/src/test/java/io/github/kamill7779/qforge/ocr/client/AnswerXmlConverterTest.java`

**验证命令：**
`mvn -pl services/ocr-service -Dtest=AnswerXmlConverterTest test`

### Task D: Redis 临时答案资产 + 查询接口

**Files:**
- Modify: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/mq/OcrResultConsumer.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/controller/OcrTaskAssetController.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/dto/OcrTaskAssetResponse.java`
- Create: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/OcrAnswerAssetRedisFlowTest.java`

**验证命令：**
`mvn -pl services/question-service -Dtest=OcrAnswerAssetRedisFlowTest test`

### Task E: 答案正式配图落库迁移

**Files:**
- Modify: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/service/QuestionCommandServiceImpl.java`
- Modify: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/repository/QuestionAssetRepository.java`
- Create: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/AnswerAssetPersistenceTest.java`

**验证命令：**
`mvn -pl services/question-service -Dtest=AnswerAssetPersistenceTest test`

### Task F: 前端消费临时答案资产并渲染

**Files:**
- Modify: `frontend/src/renderer.js`
- Modify: `frontend/test/stem-xml.test.js`

**验证命令：**
`node --test frontend/test/stem-xml.test.js`

### Task G: 文档更新

**Files:**
- Modify: `docs/question-service-api.md`
- Modify: `backend/README.md`

**验证命令：**
`rg -n "answer-assets|OCR_TASK_CONFLICT|q_answer_asset" docs/question-service-api.md backend/README.md`

### Task H: 全链路回归

**验证命令：**
```bash
cd backend
mvn -pl libs/common-contract,services/ocr-service,services/question-service,services/persist-service -am test
```

---

## 9. 验收标准

1. 不再出现对 `q_question_ocr_task` / `q_ocr_task` 增加答案字段的 DDL。
2. 同题并发发起两个答案 OCR，第二个返回 `409 OCR_TASK_CONFLICT`。
3. 答案 OCR 成功后，前端可直接看到配图渲染（不需要手工重新插图）。
4. 添加/保存答案后，答案图片存在于 `q_answer_asset`。
5. 题干图片仍只在 `q_question_asset`，两类资产不混表。

