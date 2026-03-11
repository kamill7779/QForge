# Host Env Inventory

`hosts/*.env.example` 只是模板，不是固定拓扑。

## 模板与服务对应关系

| 模板文件 | 默认角色 | 覆盖服务 |
|---|---|---|
| `core-frontend.env.example` | 核心业务 + sidecar + 前端 | `auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`、`export-sidecar`、`web-exam`、`gaokao-web` |
| `ai.env.example` | AI 处理节点 | `ocr-service`、`gaokao-corpus-service`、`gaokao-analysis-service`、`qdrant` |
| `infra.env.example` | 中间件节点 | `mysql`、`redis`、`rabbitmq`、`nacos` |
| `all-in-one.env.example` | 单机全量部署 | `mysql`、`redis`、`rabbitmq`、`nacos`、`auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`、`ocr-service`、`gaokao-corpus-service`、`gaokao-analysis-service`、`qdrant`、`export-sidecar`、`web-exam`、`gaokao-web` |

## 使用规则

1. 先复制模板为实际文件，例如 `host-a.env`。
2. 只填写每个模板顶部 `Fill These First` 区域。
3. 不要随意改 `Derived Variables`，除非你明确知道自己在调整派生关系。
4. 按真实部署调整 `COMPOSE_PROFILES`。
5. 如果只是扩一台机器并承载已有服务，不需要改 `docker-compose.remote.yml`，通常只需要新增一份 env。
6. 复制出来的真实 env 往往包含公网 IP、数据库密码和 API key，默认应保留在本机或部署系统中，不要直接提交到仓库。

补充说明：

- `GATEWAY_PUBLIC_IP` 是给前端容器内 Nginx 用的静态上游地址，不走 Nacos，属于必要配置。
- OCR 相关直连地址已从远程模板移除；后端当前通过 Nacos 获取 `ocr-service`。
- `export-sidecar` 依赖 Nacos 心跳维持临时实例，通常不需要手改 `NACOS_HEARTBEAT_INTERVAL_SECONDS`，保持默认 5 秒即可。