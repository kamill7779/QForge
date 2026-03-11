# `exam-parse-service` / `gaokao-corpus-service` 横向扩容修复方案

## 目标

- 让 `exam-parse-service` 和 `gaokao-corpus-service` 具备跨主机多副本运行前提。
- 统一把上传文件和后续重流程依赖从宿主机本地路径迁到腾讯云 COS。
- 补齐关键幂等、取消态和晚到消息防护，避免多副本下重复落库。

## 已落地范围

### 1. 共享 COS 存储抽象

- 新增 `backend/libs/storage-support`
- 提供：
  - `QForgeStorageService`
  - `CosStorageRef`
  - `QForgeStorageProperties`
  - `QForgeStorageAutoConfiguration`
- COS URI 统一格式：
  - `cos://qforge-2026-1304896342/ap-shanghai/<key>`

### 2. `gaokao-corpus-service`

- 上传改为后端接收后写 COS，不再写本地 `upload-root-dir`
- `gk_ingest_source_file.storage_ref` 统一写 COS URI
- `triggerOcrSplit` 改为：
  - 仅允许 `UPLOADED` / `OCR_FAILED`
  - `OCRING` 直接拒绝
  - `SPLIT_READY` 直接返回
  - Redis 锁 + DB 状态 CAS
- 物化改为优先向 `question-core-service` 传 `dataUri`
- `gk_question_materialization.source_hash` 启用
- `publishPaper` 基于 `gk_paper.draft_paper_id` 做幂等

### 3. `question-core-service`

- `CreateQuestionFromGaokaoRequest.AssetEntry` 新增 `dataUri`
- `GaokaoQuestionCreateService` 处理顺序改为：
  - `dataUri`
  - `storageRef` 为 `data:` URI
  - 迁移期本地路径兼容

### 4. `exam-parse-service` / `ocr-service`

- 新任务源文件改写 COS
- `q_exam_parse_source_file` 新增：
  - `storage_ref`
  - `blob_key`
  - `blob_size`
  - `checksum_sha256`
- `file_data` 保留为兼容字段
- `deleteTask` 改为 `CANCELLED`
- `ExamParseResultConsumer` 改为：
  - 先校验任务存在且未取消
  - `(task_uuid, seq_no)` 幂等更新
- `ocr-service` 读取源文件时优先从 COS 拉取，旧数据才回退 `file_data`
- `ocr-service` 对取消任务不再继续回写状态

## 配置

以下服务已接入统一存储配置：

- `gaokao-corpus-service`
- `exam-parse-service`
- `ocr-service`

配置键：

```yml
qforge:
  storage:
    backend: cos
    cos:
      bucket: qforge-2026-1304896342
      region: ap-shanghai
      endpoint: https://qforge-2026-1304896342.cos.ap-shanghai.myqcloud.com
      secret-id: ${QFORGE_STORAGE_COS_SECRET_ID:}
      secret-key: ${QFORGE_STORAGE_COS_SECRET_KEY:}
```

## Schema 变更

- `q_exam_parse_task.status` 注释补充 `CANCELLED`
- `q_exam_parse_source_file.file_data` 改为可空，并新增 COS 元数据列
- `q_exam_parse_question(task_uuid, seq_no)` 改为唯一键
- `gk_draft_paper.session_id` 改为唯一键
- `gk_paper` 新增 `draft_paper_id` 并建立唯一键
- `gk_question_materialization` 新增活动态幂等唯一键

## 当前剩余事项

- `DraftServiceImpl` 的 `edit_version` 乐观锁和 `confirmProfile` 版本 CAS 还未补完
- 缺少历史本地路径 / `file_data` 到 COS 的后台回填任务
- 生产环境落地前需要执行显式 DDL 迁移，不能仅依赖现有 `CREATE TABLE IF NOT EXISTS`

## 验证记录

- `libs/storage-support` / `internal-api-contract` / `question-service` 定向测试通过
- `gaokao-corpus-service` 定向测试通过
- `exam-parse-service` / `ocr-service` 编译通过
- 全量 `ocr-service` 测试被仓库既有 smoke case 阻塞：
  - `OfficialGlmOcrLayoutParsingSmokeTest`
  - 原因：当前环境缺少 `ZHIPU_API_KEY`
