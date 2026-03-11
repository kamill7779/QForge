# QForge Remote Deploy

这套目录用于多台通过云内网互通的轻量云服务器部署。

## 目标

- 所有后端服务向 Nacos 注册宿主机内网 IP:PORT
- 宿主机之间通过云内网直接互通，服务间默认走内网地址，不再依赖公网访问回流
- 使用 `docker-compose.remote.yml` + `hosts/*.env.example` 管理多机部署分组

## 文件说明

- `docker-compose.remote.yml`
  - 远程部署 compose，按 `core`、`ai`、`sidecar`、`frontend`、`infra` 分 profile
- `setup-host.sh`
  - 清理旧的 loopback 公网 IP 绑定与 systemd 遗留，仅用于从历史公网方案迁移
- `deploy.sh`
  - 以指定 env 文件执行远程 compose
- `hosts/*.env.example`
  - 主机角色模板，按实际机器复制为私有 env 文件后使用
- `hosts/all-in-one.env.example`
  - 单机部署全部环境的模板，适合一台机器承载全部服务
- `enable-public-hairpin-nat.sh`
  - 旧方案，保留仅用于回滚，不再作为默认部署路径
- `remote-stack.env`
  - 旧方案 env，保留仅用于回滚

## 推荐工作流

1. 复制一个 env 模板并填入真实内网 IP、凭据和中间件地址
2. 如机器曾经使用过公网 loopback 方案，先运行 `sudo ./setup-host.sh hosts/<your-host>.env cleanup`
3. 用 `./deploy.sh hosts/<your-host>.env up -d --build` 启动该机 profile 对应服务
4. 用 `./deploy.sh hosts/<your-host>.env ps` / `logs -f <service>` 排查问题

复制出来的真实 host env 文件通常包含公网 IP、密码或 API key，应视为私有部署文件，不要直接提交到仓库。

## 填写方式

所有 env 模板都分成两段：

- `Fill These First`
  - 只填写这一段，包括本机内网 IP、角色相关的对端内网 IP、凭据和端口
- `Derived Variables`
  - 由上方变量自动拼装，通常不需要改

注意：这些模板包含变量引用，因此推荐始终通过 [backend/deploy/deploy.sh](backend/deploy/deploy.sh) 和 [backend/deploy/setup-host.sh](backend/deploy/setup-host.sh) 使用。它们会先用 shell 加载 env，再执行 compose。不要直接依赖 `docker compose --env-file` 去解析这些派生变量。

## Profiles

- `infra`
  - `mysql`、`redis`、`rabbitmq`、`nacos`
- `core`
  - `auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`
- `ai`
  - `ocr-service`、`gaokao-corpus-service`、`gaokao-analysis-service`、`qdrant`
- `sidecar`
  - `export-sidecar`
- `frontend`
  - `web-exam`、`gaokao-web`

## Env 文件与服务映射

| Env 文件 | 典型主机角色 | 对应服务 |
|---|---|---|
| `hosts/infra.env.example` | 中间件机 | `mysql`、`redis`、`rabbitmq`、`nacos` |
| `hosts/core-frontend.env.example` | 核心业务 + 前端机 | `auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`、`export-sidecar`、`web-exam`、`gaokao-web` |
| `hosts/ai.env.example` | AI 计算机 | `ocr-service`、`gaokao-corpus-service`、`gaokao-analysis-service`、`qdrant` |
| `hosts/all-in-one.env.example` | 单机全量部署 | `mysql`、`redis`、`rabbitmq`、`nacos`、`auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`、`ocr-service`、`gaokao-corpus-service`、`gaokao-analysis-service`、`qdrant`、`export-sidecar`、`web-exam`、`gaokao-web` |

如果要新增机器但只承载部分服务，不需要改 compose 文件，可以直接复制其中一个 env 模板后，通过 `COMPOSE_PROFILES` 和 `./deploy.sh <env> up -d --build <service...>` 选择实际启动的服务。

多机最终推荐落点、JVM 建议值、可加副本服务和高峰期扩容顺序，见 [backend/deploy/final-deployment-plan.md](/home/ubuntu/QForge/backend/deploy/final-deployment-plan.md)。

## 前端容器确认

远程部署里两个前端服务都有对应 Docker 容器：

| 前端 | Compose 服务名 | Docker 容器名 | 默认映射端口 |
|---|---|---|---|
| Web 题库/组卷前端 | `web-exam` | `qforge-web-exam` | `${WEB_EXAM_PORT}:80`，默认 `5173:80` |
| 高考语料前端 | `gaokao-web` | `qforge-gaokao-web` | `${GAOKAO_WEB_PORT}:80`，默认 `5175:80` |

它们都定义在 [backend/deploy/docker-compose.remote.yml](backend/deploy/docker-compose.remote.yml)，仍然使用 bridge + 端口映射，不使用 host 网络。

## 地址占位规则

- `hosts/*.env.example` 中出现的 URL 和 IP 现在只表示“应该填什么类型的地址”，不是固定拓扑。
- 新增机器时必须复制模板并替换顶部填写区中的内网主机 IP、凭据和对端地址变量。
- 只要这些地址来自 env，而不是写死在 compose 或 Dockerfile 中，就不会影响后续横向扩容。

## 为什么需要 GATEWAY_PRIVATE_IP

- `GATEWAY_PRIVATE_IP` 是合理依赖，因为两个前端容器里的 Nginx 是静态反向代理，不接入 Nacos，也不会使用 Spring Cloud 服务发现。
- 前端要把 `/api/` 和 `/ws/` 转发到哪个网关实例，只能在启动时通过 `GATEWAY_UPSTREAM` 这种静态变量注入。

## 为什么不再需要 OCR_PUBLIC_IP

- 当前 `question-core-service` 已通过 Feign + Nacos 调用 `ocr-service`，不需要额外的直连 URL。
- 远程部署已经移除了 `OCR_DIRECT_BASE_URL` / `OCR_PUBLIC_IP` 这层配置，避免形成不必要的拓扑耦合。
- `export-sidecar` 仍保留 direct-url fallback，是因为 [ExportService.java](backend/services/exam-service/src/main/java/io/github/kamill7779/qforge/exam/service/ExportService.java#L33) 明确实现了 Feign 失败后的 direct 调用降级。

## 注意事项

- 远程前端容器仍使用 bridge + 端口映射；只有后端服务和 sidecar 使用 host 网络
- 后端服务注册到 Nacos 时统一广播 `${APP_NODE_IP}`，默认应是宿主机内网 IP
- 前端容器代理网关时使用内网网关地址，例如 `${GATEWAY_PRIVATE_IP}:${GATEWAY_PORT}`，因此远程场景不依赖 Docker DNS
- 如果 `exam-service` 和 `export-sidecar` 不在同一台机器，需在 env 中显式设置 `EXAM_EXPORT_SIDECAR_BASE_URL`
- 如果 `gaokao-analysis-service` 和 `qdrant` 不在同一台机器，需在 env 中显式设置 `QDRANT_HOST`
- `export-sidecar` 使用 `nacos-sdk-python==1.0.0` 时必须保持心跳开启；远程 compose 已默认注入 `NACOS_HEARTBEAT_INTERVAL_SECONDS=5`。如果去掉这个参数，sidecar 可能会打印注册成功，但 Nacos `instance/list` 的 `hosts` 仍为空，`exam-service` 会退回 direct-url fallback
- `export-sidecar` 的健康检查路径是 `/api/export/health`，不是 `/health`
- 旧的公网 loopback / hairpin NAT 方案已经废弃；迁移完成后应清理 `qforge-loopback-ip.service`、`/usr/local/bin/qforge-bind-loopback-ip.sh` 和 `lo` 上残留的公网 `/32`
