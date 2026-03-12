# gaokao 子系统移除计划

## 1. 目标

- 从仓库和运行链路中移除 `gaokao-corpus-service`、`gaokao-analysis-service`、`gaokao-web`。
- 移除所有 `gk_*` 数据库模式与初始化脚本，并直接清理远程数据库中的同名表。
- 清理 `prompt.md`、部署脚本、compose、README、docs/plan 中的 gaokao 事实，避免后续任务再把它当成现役子系统。
- 保留并验证 `exam-parse-service`、`ocr-service`、`question-core-service` 的非 gaokao 能力不受影响。

## 2. 安全边界

### 2.1 必须删除

- `backend/services/gaokao-corpus-service`
- `backend/services/gaokao-analysis-service`
- `gaokao-web`
- `backend/sql/gaokao-schema.sql`
- `common-contract` 中 gaokao MQ 契约
- `internal-api-contract` 中 gaokao 物化 / 分题契约
- `question-core-service` 的 `/internal/questions/from-gaokao`
- `gateway-service` 的 `/api/gaokao/**` 路由
- 部署、文档、提示词里的 gaokao 服务、Qdrant、`gk_*` 说明

### 2.2 必须保留

- `exam-parse-service` 的解析任务主链路
- `ocr-service` 的 exam-parse 相关 OCR、拆题、答案清洗、XML 生成能力
- `storage-support` 共享存储抽象
- `question-core-service` 的正式题目主数据能力

### 2.3 `ocr-service` 清理策略

- 删除 gaokao 专用入口：
  - `/internal/ocr/gaokao-split`
  - `/internal/ocr/recognize`
- 删除 gaokao 专用实现：
  - `GaokaoSplitService`
  - `GaokaoSplitController`
  - `OcrRecognizeController`
- 保留 `exam-parse` 共用组件：
  - `MultiPageOcrAggregator`
  - `ExamSplitLlmClient`
  - `ExamParseOutputParser`
  - `ExamImageCropper`
  - `ExamQuestionXmlGenerator`

结论：gaokao 在 `ocr-service` 的痕迹以“专用控制器 + 专用编排服务”的形式存在，删除这些入口不会影响 `exam-parse` 已在使用的底层 OCR / 拆题能力。

## 3. 执行顺序

### Phase 1：切断构建与运行依赖

1. 从 `backend/pom.xml` 删除 gaokao 模块。
2. 从 gateway、compose、deploy、README 中删除 gaokao / qdrant 运行入口。
3. 删除 `question-core-service`、`internal-api-contract`、`common-contract`、`ocr-service` 中的 gaokao 契约与入口。

### Phase 2：删除仓库内容

1. 删除两个后端服务目录。
2. 删除 `gaokao-web` 项目。
3. 删除 `gaokao-schema.sql`。
4. 删除或改写所有仍宣称 gaokao 为现役能力的 docs / plan / prompt。

### Phase 3：删除远程数据库对象

1. 连接远程 `qforge` MySQL。
2. 以子表优先顺序执行 `DROP TABLE IF EXISTS gk_*`。
3. 用 `SHOW TABLES LIKE 'gk\\_%'` 验证远程库清空。

## 4. 验证要求

- Maven 编译至少覆盖：
  - `libs/common-contract`
  - `libs/internal-api-contract`
  - `libs/storage-support`
  - `services/gateway-service`
  - `services/question-service`
  - `services/exam-parse-service`
  - `services/ocr-service`
- `rg -n "gaokao|gk_|Qdrant|/api/gaokao|/internal/questions/from-gaokao"` 结果只允许出现在本次移除说明文档或 Git 历史无关文件中。
- 远程数据库 `SHOW TABLES LIKE 'gk\\_%'` 返回空。

## 5. 风险提示

- 远程数据库删除是不可逆动作，本次按用户要求直接删除，不做备份。
- 历史提交中仍会保留 gaokao 内容，本次目标仅是移除当前工作树与远程现网库。
- `storage-support` 中 COS 能力继续保留，因为 `exam-parse-service` 仍使用它，不属于 gaokao 专属代码。
