# Host Env Inventory

`hosts/*.env.example` 只是模板，不是固定拓扑。

| 模板文件 | 默认角色 | 覆盖服务 |
|---|---|---|
| `core-frontend.env.example` | 核心业务 + sidecar + 前端 | `auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`、`export-sidecar`、`web-exam` |
| `ai.env.example` | OCR 节点 | `ocr-service` |
| `infra.env.example` | 中间件节点 | `mysql`、`redis`、`rabbitmq`、`nacos` |
| `all-in-one.env.example` | 单机全量部署 | `mysql`、`redis`、`rabbitmq`、`nacos`、`auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`、`ocr-service`、`export-sidecar`、`web-exam` |
