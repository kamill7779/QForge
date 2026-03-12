# QForge Remote Deploy

这套目录用于多台通过云内网互通的轻量云服务器部署。

## Profiles

- `infra`
  - `mysql`、`redis`、`rabbitmq`、`nacos`
- `core`
  - `auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`
- `ai`
  - `ocr-service`
- `sidecar`
  - `export-sidecar`
- `frontend`
  - `web-exam`

## Env 文件与服务映射

| Env 文件 | 典型主机角色 | 对应服务 |
|---|---|---|
| `hosts/infra.env.example` | 中间件机 | `mysql`、`redis`、`rabbitmq`、`nacos` |
| `hosts/core-frontend.env.example` | 核心业务 + 前端机 | `auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`、`export-sidecar`、`web-exam` |
| `hosts/ai.env.example` | OCR 节点 | `ocr-service` |
| `hosts/all-in-one.env.example` | 单机全量部署 | `mysql`、`redis`、`rabbitmq`、`nacos`、`auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`、`ocr-service`、`export-sidecar`、`web-exam` |

## Secrets

把 COS 云凭证放入 `backend/deploy/remote-stack.secrets.local.env` 这类本地 secrets 文件，通过 `deploy.sh` 自动加载；不要把明文密钥写进受版本控制的 env、compose 或文档。
