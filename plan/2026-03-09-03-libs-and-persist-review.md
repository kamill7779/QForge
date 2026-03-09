# libs 与 persist-service 评审

## 结论

- `libs` 没有被完全忽略，但边界文档和测试明显落后于实际架构。
- `persist-service` 不是“通用持久化服务”，而是“异步任务写回服务”；代码已经是这个事实，文档还没有完全跟上。

## 发现的问题

### 1. `common-contract` 与 `internal-api-contract` 的职责需要显式区分

- 现状
  - `common-contract` 承载异步事件
  - `internal-api-contract` 承载 `question-core-service` 内部 Feign DTO/Client
- 问题
  - 仓库顶层文档仍以 `common-contract` 为主描述
  - 真实的同步微服务契约没有被清晰强调

### 2. 共享常量存在复制

- 现状
  - `exam-parse-service` 与 `question-core-service` 各自定义了 `qforge:ws:push`
- 本轮处理
  - 已下沉到 `common-contract.RedisChannelNames`

### 3. 存在历史依赖滞留

- 现状
  - `auth-service` / `gateway-service` / `exam-service` 带着未使用的 `common-contract`
- 本轮处理
  - 已清理这些未使用依赖

### 4. `persist-service` 的定位没有被写清

- 现状
  - 代码看起来是 OCR/AI 任务结果的 MQ 写回 sink
  - 但从命名上容易被继续误用成“统一持久化入口”
- 风险
  - 后续功能开发可能继续把不相关写库职责往里塞

### 5. 共享契约缺少测试护栏

- `common-contract`
  - 缺系统化事件序列化兼容测试
- `internal-api-contract`
  - 缺 Feign DTO 的 JSON 兼容测试
- `persist-service`
  - 缺针对各类 write-back 事件的幂等和路由测试

## 本轮已处理项

- 收敛 WS Redis channel 常量到共享层
- 清理明显未使用的共享依赖
- 更新后端架构文档，明确:
  - `common-contract` = 异步事件契约
  - `internal-api-contract` = 同步内部 API 契约
  - `persist-service` = 异步写回服务

## 建议修复计划

1. 为 `common-contract` 增加事件序列化兼容测试:
   - OCR
   - AI
   - exam-parse
   - DB write-back
2. 为 `internal-api-contract` 增加 DTO 序列化兼容测试:
   - `QuestionSummaryDTO`
   - `QuestionFullDTO`
   - `CreateQuestionFromParseRequest/Response`
3. 为 `persist-service` 增加消费者测试:
   - 正常写回
   - 重复事件
   - 不同 task type 路由
4. 保持 `persist-service` 小而专:
   - 不接入通用 CRUD
   - 不变成 repository facade
   - 只处理异步结果落库
