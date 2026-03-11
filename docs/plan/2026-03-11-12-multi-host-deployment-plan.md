# QForge 多轻量云服务器部署方案

> 日期：2026-03-11  
> 状态：方案设计  
> 前置文档：`docs/deploy/deploy01.md`

---

## 1. 当前问题总结

### 1.1 核心矛盾

QForge 后端 12 个服务（含 export-sidecar）部署在**多台无内网互通的轻量云服务器**上，每台服务器只有公网 IP 和一个云厂商分配的私网 IP（如 `10.0.0.x`，但跨机不可达）。这产生了一个三难困境：

| 注册方式 | 跨机访问 | 同机容器互访 | 管理复杂度 |
|---|---|---|---|
| Docker 内网 IP（172.x） | ✗ 不可达 | ✓ 同 bridge 内直通 | 低 |
| 公网 IP | ✓ 可达 | ✗ 容器→公网 IP 回环失败 | 低 |
| 公网 IP + iptables hairpin | ✓ 可达 | ✓ 需 DNAT 回环 | **高** |

当前方案选择了第三种，通过 `enable-public-hairpin-nat.sh` 为 Docker bridge 子网添加 iptables DNAT + MASQUERADE 规则，实现同机容器通过公网 IP 回流到宿主机私网地址。

### 1.2 当前 hairpin NAT 方案的具体痛点

1. **iptables 规则脆弱**
   - 脚本必须动态探测 Docker bridge 子网名称、子网 CIDR，任何 Docker 网络重建都可能导致规则失效
   - 旧规则残留会与新规则冲突，历史上已因此导致 `connection refused`
   - 规则只针对 Docker 子网的 source IP，对 host 上直接运行的进程无效

2. **每台机器都要维护 iptables 生命周期**
   - 需要 systemd service 保证开机执行
   - Docker 重启/网络重建后需要重新执行
   - 调试时需要在多台机器上分别检查 `iptables -t nat -S`，难以排查

3. **单一 docker-compose.yml 不适配多机部署**
   - 当前 `docker-compose.yml`（远程版）定义了所有 12 个服务，但实际上不同机器只跑其中一部分
   - 通过 env 中 `START_AUTH=false` 等变量控制，但 compose 文件没有 profile 支持，容易误启动
   - `depends_on` 声明了全局依赖链（如 gateway 依赖所有微服务），但跨机场景下这些依赖无法在单机 compose 中满足

4. **每台机器的 env 文件是硬编码的**
   - `remote-stack.env` 中 `APP_PUBLIC_HOST=111.229.17.125` 写死了一台机器的公网 IP
   - 没有「哪台机器跑哪些服务」的声明式 inventory
   - 增加新机器时需要手工复制并修改 env，容易出错

5. **Docker DNS 与公网注册的混用**
   - 部分服务用 Docker 容器名直接引用（如 `QDRANT_HOST=qdrant`、`EXPORT_SIDECAR_DIRECT_URL=http://export-sidecar:8092`）
   - 但 Nacos 注册的是公网 IP:PORT
   - 这种混用只有在所有服务跑在同一个 compose 网络内才成立，一旦拆到不同机器就崩溃

---

## 2. 方案设计

### 2.1 核心策略：Host 网络模式 + Loopback IP 绑定

**用 Docker `network_mode: host` 替代 bridge 模式。**

原理：

```
Bridge 模式（当前）:
  容器 → 172.18.0.x:PORT → Docker bridge → 宿主机端口映射 → 外部

Host 模式（目标）:
  容器 → 直接共享宿主机网络栈 → 0.0.0.0:PORT → 外部
```

Host 模式下：
- 容器内进程直接监听宿主机端口，**没有 Docker 内网 IP 问题**
- `SPRING_CLOUD_NACOS_DISCOVERY_IP` 设为公网 IP，Nacos 注册公网地址
- 同机服务通过 `127.0.0.1:PORT` 互访——但 Feign/LoadBalancer 走 Nacos 获取的是公网 IP

为解决"同机进程通过公网 IP 访问自身"的问题，使用 **Loopback IP 绑定**：

```bash
ip addr add ${PUBLIC_IP}/32 dev lo
```

这条命令的效果：
- 宿主机上任何进程（包括 host 模式容器）访问 `PUBLIC_IP:PORT` 时，内核直接在 lo 接口本地投递，**不出公网**
- 云厂商的入站 NAT 不受影响（入站流量走 eth0，目标是私网 IP）
- 无需 iptables 规则，无需 Docker 子网探测，无需 MASQUERADE

对比：

| | Hairpin NAT (当前) | Loopback IP (目标) |
|---|---|---|
| 配置复杂度 | 高：iptables chain + DNAT + MASQUERADE + 子网探测 | 极低：一条 `ip addr add` |
| Docker 网络重建 | 需要重新执行脚本 | 不受影响 |
| 适用范围 | 仅 Docker bridge 子网 | 宿主机所有进程 |
| 调试难度 | 需要 `iptables -t nat -S` 分析规则链 | `ip addr show lo` 即可确认 |
| 开机持久化 | systemd service + 脚本 | 一行 netplan/ifcfg 或 systemd-networkd |

### 2.2 多机服务分布管理

#### 2.2.1 引入 Compose profiles

用 Docker Compose profiles 将所有服务分为若干**部署组**，每台机器通过 `COMPOSE_PROFILES` 环境变量选择要启动的组：

```
profile: infra      → MySQL, Redis, RabbitMQ, Nacos (通常只在一台机器)
profile: core       → gateway, auth, question-core, exam, exam-parse, question-basket, persist
profile: ai         → ocr, gaokao-corpus, gaokao-analysis, qdrant
profile: sidecar    → export-sidecar
profile: frontend   → web, gaokao-web
```

一个服务可以属于多个 profile。每台机器在 `.env` 中声明：

```bash
COMPOSE_PROFILES=core,sidecar,frontend
```

这样 `docker compose up -d` 只会启动属于这些 profile 的服务。

#### 2.2.2 Per-host env 文件

```
backend/deploy/hosts/
  host-a.env        # 例：跑 core + sidecar + frontend
  host-b.env        # 例：跑 infra + ai
  README.md         # inventory 说明
```

建议在实际使用中维持一张 inventory 表，明确每个 env 文件对应的主机和服务：

| Env 文件 | 典型角色 | 对应服务 |
|---|---|---|
| `hosts/infra.env.example` | 中间件机 | `mysql`、`redis`、`rabbitmq`、`nacos` |
| `hosts/core-frontend.env.example` | 核心业务 + 前端机 | `auth-service`、`gateway-service`、`question-core-service`、`question-basket-service`、`exam-service`、`exam-parse-service`、`persist-service`、`export-sidecar`、`web-exam`、`gaokao-web` |
| `hosts/ai.env.example` | AI 计算机 | `ocr-service`、`gaokao-corpus-service`、`gaokao-analysis-service`、`qdrant` |

如果未来新增机器，只要复制一个最接近的 env 模板，替换该主机的地址与凭据，然后按需调整 `COMPOSE_PROFILES` 或启动命令里的服务列表即可，不需要复制一份新的 compose。

每个 env 文件内容结构建议拆成两段：

- `Fill These First`
  - 只填写主机 IP、角色对应的对端 IP、凭据、端口
- `Derived Variables`
  - 用上方变量自动生成最终 compose 所需的变量

示例：

```bash
# ===== Fill These First =====
SELF_PUBLIC_IP=<host-public-ip>    # 本机公网 IP
SELF_PRIVATE_IP=<host-private-ip>  # 本机私网 IP
INFRA_PUBLIC_IP=<infra-public-ip>  # 中间件机器公网 IP
GATEWAY_PUBLIC_IP=<gateway-public-ip>
SIDECAR_PUBLIC_IP=<sidecar-public-ip>

COMPOSE_PROFILES=core,sidecar,frontend

# ===== Derived Variables =====
APP_PUBLIC_HOST=${SELF_PUBLIC_IP}
APP_LOCAL_IP=${SELF_PRIVATE_IP}

MYSQL_HOST=${INFRA_PUBLIC_IP}
MYSQL_PORT=13306

EXAM_EXPORT_SIDECAR_BASE_URL=http://${SIDECAR_PUBLIC_IP}:8092
FRONTEND_GATEWAY_UPSTREAM=${GATEWAY_PUBLIC_IP}:8080
GAOKAO_FRONTEND_GATEWAY_UPSTREAM=${GATEWAY_PUBLIC_IP}:8080
```

这里的地址都不应该被理解为固定拓扑。它们必须保留在 env 层，而不是写死在 compose 或应用配置中。只要地址是 env 变量，新增机器时只需要改顶部填写区，不会阻碍扩容。

需要区分两类依赖：

- `GATEWAY_PUBLIC_IP`
  - 合理且必要，因为 `web-exam` / `gaokao-web` 的 Nginx 不接 Nacos，必须知道要代理到哪个公网网关地址。
- `OCR_PUBLIC_IP`
  - 当前不合理，`question-core-service` 已经通过 Feign + Nacos 发现 `ocr-service`，不应再要求部署模板额外配置 OCR 直连地址。
  - 因此远程部署模板已移除这层配置。

另外，建议提供一份单机全量模板，例如 `hosts/all-in-one.env.example`，把 `COMPOSE_PROFILES` 直接设为 `infra,core,ai,sidecar,frontend`，这样单机验证或小规模部署时可以一键拉起全部服务。

#### 2.2.3 去掉跨机 depends_on

Host 模式下不再有 Docker 网络隔离，`depends_on` 只用于同机服务的启动顺序。跨机依赖通过 Nacos 服务发现的健康检查保障（Feign 自带重试），不在 compose 中声明。

### 2.3 网络拓扑示意

```
                        Public Internet
                ┌──────────┬──────────┐
                │          │          │
         ┌──────▼───┐ ┌───▼──────┐ ┌─▼──────────┐
         │  Host A   │ │  Host B  │ │  Host C    │
         │  PUB: a   │ │  PUB: b  │ │  PUB: c    │
         │  lo: +a   │ │  lo: +b  │ │  lo: +c    │
         │           │ │          │ │            │
         │ gateway   │ │ nacos    │ │ ocr        │
         │ auth      │ │ mysql    │ │ gaokao-*   │
         │ q-core    │ │ redis    │ │ qdrant     │
         │ exam      │ │ rabbitmq │ │            │
         │ q-basket  │ │          │ │            │
         │ exam-parse│ │          │ │            │
         │ persist   │ │          │ │            │
         │ sidecar   │ │          │ │            │
         │ web       │ │          │ │            │
         │ gaokao-web│ │          │ │            │
         └───────────┘ └──────────┘ └────────────┘

同机调用: 进程 → PUBLIC_IP:PORT → lo 本地投递 → 目标进程
跨机调用: 进程 → PUBLIC_IP:PORT → 公网路由 → 对端宿主机 → 目标进程
Nacos:    所有服务注册 PUBLIC_IP:PORT，consumer 通过 Nacos 获取地址
```

上图为示意；实际如何分布取决于各机器资源。两台机器也能用这个方案，一台全部服务也行。

---

## 3. 实施方案

### 3.1 文件结构变更

```
backend/deploy/
  docker-compose.remote.yml      # [改造] 远程部署专用，全部 host 网络 + profiles
  hosts/
    host-a.env                   # [新增] 机器 A 的配置
    host-b.env                   # [新增] 机器 B 的配置（按需增加）
    README.md                    # [新增] 部署 inventory 文档
  setup-host.sh                  # [新增] 单机初始化脚本（loopback IP + 持久化）
  deploy.sh                      # [新增] 部署/更新脚本
  enable-public-hairpin-nat.sh   # [保留] 旧方案，标记为 deprecated
  remote-stack.env               # [保留] 旧 env，标记为 deprecated
```

本地开发文件不变：
- `docker-compose.dev.yml` → bridge 网络 + Docker DNS，全服务单机，不受影响

### 3.2 setup-host.sh —— 单机初始化

每台新机器首次部署时执行一次：

```bash
#!/usr/bin/env bash
set -euo pipefail

# 读取本机 env
ENV_FILE="${1:?用法: setup-host.sh <host-env-file>}"
set -a; . "$ENV_FILE"; set +a

PUBLIC_IP="${APP_PUBLIC_HOST:?需要 APP_PUBLIC_HOST}"

# 1. 添加公网 IP 到 lo（幂等）
if ! ip addr show lo | grep -q "$PUBLIC_IP"; then
  ip addr add "$PUBLIC_IP/32" dev lo
  echo "[setup] 已添加 $PUBLIC_IP/32 到 lo"
else
  echo "[setup] lo 已有 $PUBLIC_IP，跳过"
fi

# 2. 持久化（适配 netplan / 非 netplan 系统）
if command -v netplan &>/dev/null; then
  NETPLAN_FILE="/etc/netplan/99-qforge-loopback.yaml"
  cat > "$NETPLAN_FILE" <<EOF
network:
  version: 2
  ethernets:
    lo:
      addresses:
        - ${PUBLIC_IP}/32
EOF
  chmod 600 "$NETPLAN_FILE"
  echo "[setup] 已写入 $NETPLAN_FILE（netplan apply 后永久生效）"
else
  # fallback: systemd-networkd drop-in 或 rc.local
  RC_LOCAL="/etc/rc.local"
  LINE="ip addr add ${PUBLIC_IP}/32 dev lo 2>/dev/null || true"
  if [[ -f "$RC_LOCAL" ]] && grep -qF "$LINE" "$RC_LOCAL"; then
    echo "[setup] rc.local 已包含 loopback 配置"
  else
    echo "$LINE" >> "$RC_LOCAL"
    chmod +x "$RC_LOCAL"
    echo "[setup] 已追加到 $RC_LOCAL"
  fi
fi

# 3. 验证
echo "[setup] 验证: 从本机 curl 公网 IP..."
if curl -sf --connect-timeout 2 "http://${PUBLIC_IP}:${NACOS_SERVER_ADDR##*:}/nacos/" >/dev/null 2>&1; then
  echo "[setup] Nacos 可达 ✓"
else
  echo "[setup] 注意: Nacos 暂不可达（可能是本机没有 Nacos，这是正常的）"
fi

echo "[setup] 完成。请运行 deploy.sh 部署服务。"
```

### 3.3 docker-compose.remote.yml —— 远程部署模板

关键改造点：

1. **所有业务服务** 使用 `network_mode: host`
2. **移除所有 `ports` 映射**（host 模式不需要）
3. **移除 bridge 网络定义**
4. **为每个服务添加 `profiles`**
5. **同机直连地址统一用环境变量**（从 per-host env 注入）
6. **跨机依赖的 `depends_on` 移除**，仅保留同 profile 内的启动顺序

示例（关键服务片段）：

```yaml
services:
  # ─── Core Profile ───
  auth-service:
    profiles: [core]
    build:
      context: ../..     # 指向 backend/
      dockerfile: Dockerfile
      args: { SERVICE_NAME: auth-service }
    container_name: auth-service
    restart: unless-stopped
    network_mode: host
    mem_limit: 384m
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SERVER_PORT: 8088
      SPRING_CLOUD_NACOS_DISCOVERY_IP: ${APP_PUBLIC_HOST}
      SPRING_CLOUD_NACOS_DISCOVERY_PORT: 8088
      JAVA_TOOL_OPTIONS: ${JAVA_OPTS_AUTH:--Xms128m -Xmx256m}
      MYSQL_HOST: ${MYSQL_HOST}
      MYSQL_PORT: ${MYSQL_PORT}
      MYSQL_DB: ${MYSQL_DB}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PORT: ${REDIS_PORT}
      NACOS_SERVER_ADDR: ${NACOS_SERVER_ADDR}
      JWT_SECRET: ${JWT_SECRET}

  gateway-service:
    profiles: [core]
    build:
      context: ../..
      dockerfile: Dockerfile
      args: { SERVICE_NAME: gateway-service }
    container_name: gateway-service
    restart: unless-stopped
    network_mode: host
    mem_limit: 512m
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SERVER_PORT: 8080
      SPRING_CLOUD_NACOS_DISCOVERY_IP: ${APP_PUBLIC_HOST}
      SPRING_CLOUD_NACOS_DISCOVERY_PORT: 8080
      JAVA_TOOL_OPTIONS: ${JAVA_OPTS_GATEWAY:--Xms128m -Xmx256m}
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PORT: ${REDIS_PORT}
      NACOS_SERVER_ADDR: ${NACOS_SERVER_ADDR}
      JWT_SECRET: ${JWT_SECRET}
    depends_on:
      auth-service: { condition: service_started }

  question-core-service:
    profiles: [core]
    build:
      context: ../..
      dockerfile: Dockerfile
      args: { SERVICE_NAME: question-service }
    container_name: question-core-service
    restart: unless-stopped
    network_mode: host
    mem_limit: 768m
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SERVER_PORT: 8089
      SPRING_CLOUD_NACOS_DISCOVERY_IP: ${APP_PUBLIC_HOST}
      SPRING_CLOUD_NACOS_DISCOVERY_PORT: 8089
      JAVA_TOOL_OPTIONS: ${JAVA_OPTS_QUESTION_CORE:--Xms128m -Xmx256m}
      MYSQL_HOST: ${MYSQL_HOST}
      MYSQL_PORT: ${MYSQL_PORT}
      MYSQL_DB: ${MYSQL_DB}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PORT: ${REDIS_PORT}
      RABBITMQ_HOST: ${RABBITMQ_HOST}
      RABBITMQ_PORT: ${RABBITMQ_PORT}
      RABBITMQ_USER: ${RABBITMQ_USER}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD}
      NACOS_SERVER_ADDR: ${NACOS_SERVER_ADDR}
      # OCR_SERVICE_BASE_URL 不再需要——Feign 通过 Nacos 发现 ocr-service

  # ─── AI Profile ───
  qdrant:
    profiles: [ai]
    image: qdrant/qdrant:v1.12.1
    container_name: qforge-qdrant
    restart: unless-stopped
    network_mode: host        # 监听 0.0.0.0:6333
    mem_limit: 768m
    volumes:
      - qforge-qdrant-data:/qdrant/storage

  gaokao-analysis-service:
    profiles: [ai]
    build:
      context: ../..
      dockerfile: Dockerfile
      args: { SERVICE_NAME: gaokao-analysis-service }
    container_name: gaokao-analysis-service
    restart: unless-stopped
    network_mode: host
    mem_limit: 1536m
    environment:
      SPRING_PROFILES_ACTIVE: dev
      SERVER_PORT: 8096
      SPRING_CLOUD_NACOS_DISCOVERY_IP: ${APP_PUBLIC_HOST}
      SPRING_CLOUD_NACOS_DISCOVERY_PORT: 8096
      JAVA_TOOL_OPTIONS: ${JAVA_OPTS_GAOKAO_ANALYSIS:--Xms192m -Xmx384m}
      MYSQL_HOST: ${MYSQL_HOST}
      # ... 其他中间件地址
      QDRANT_HOST: 127.0.0.1   # 同 profile 同机
      QDRANT_PORT: 6333
      NACOS_SERVER_ADDR: ${NACOS_SERVER_ADDR}
      ZHIPUAI_API_KEY: ${ZHIPUAI_API_KEY}
    depends_on:
      qdrant: { condition: service_started }

  # ─── Sidecar Profile ───
  export-sidecar:
    profiles: [sidecar]
    build:
      context: ../../export-sidecar
      dockerfile: Dockerfile
    container_name: qforge-export-sidecar
    restart: unless-stopped
    network_mode: host
    mem_limit: 512m
    environment:
      NACOS_SERVER_ADDR: ${NACOS_SERVER_ADDR}
      SERVICE_PORT: 8092
      SERVICE_IP: ${APP_PUBLIC_HOST}

  # ─── Frontend Profile ───
  web-exam:
    profiles: [frontend]
    build:
      context: ../../web
      dockerfile: Dockerfile
    container_name: qforge-web-exam
    restart: unless-stopped
    mem_limit: 256m
    environment:
      GATEWAY_UPSTREAM: ${FRONTEND_GATEWAY_UPSTREAM}
    ports:
      - "${WEB_EXAM_PORT:-5173}:80"

  gaokao-web:
    profiles: [frontend]
    build:
      context: ../../gaokao-web
      dockerfile: Dockerfile
    container_name: qforge-gaokao-web
    restart: unless-stopped
    mem_limit: 256m
    environment:
      GATEWAY_UPSTREAM: ${GAOKAO_FRONTEND_GATEWAY_UPSTREAM}
    ports:
      - "${GAOKAO_WEB_PORT:-5175}:80"

volumes:
  qforge-qdrant-data:
```

这里需要明确：两个前端在远程部署中都有独立 Docker 容器，分别是 `web-exam` 和 `gaokao-web`。它们不注册到 Nacos，也不需要 host 网络，而是继续通过 bridge + 端口映射对外提供页面。

### 3.4 deploy.sh —— 部署脚本

```bash
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${1:?用法: deploy.sh <host-env-file> [docker-compose 参数...]}"
shift

# 加载 env
set -a; . "$ENV_FILE"; set +a
export COMPOSE_PROFILES

COMPOSE_FILE="$SCRIPT_DIR/docker-compose.remote.yml"

echo "[deploy] 机器: ${APP_PUBLIC_HOST}"
echo "[deploy] Profiles: ${COMPOSE_PROFILES}"
echo "[deploy] Compose: $COMPOSE_FILE"

# 检查 loopback IP
if ! ip addr show lo | grep -q "${APP_PUBLIC_HOST}"; then
  echo "[deploy] 警告: lo 未绑定 ${APP_PUBLIC_HOST}，先执行 setup-host.sh"
  exit 1
fi

# 执行 compose
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" "$@"
```

用法：

```bash
# 构建并启动该机器的所有 profile 服务
./deploy.sh hosts/host-a.env up -d --build

# 只重建某个服务
./deploy.sh hosts/host-a.env up -d --build gateway-service

# 查看日志
./deploy.sh hosts/host-a.env logs -f gateway-service
```

### 3.5 host-a.env 示例

```bash
# === 本机身份 ===
APP_PUBLIC_HOST=111.229.17.125
APP_LOCAL_IP=10.0.0.15
COMPOSE_PROFILES=core,sidecar,frontend

# === 中间件 (指向 infra 机器) ===
MYSQL_HOST=49.235.113.163
MYSQL_PORT=13306
MYSQL_DB=qforge
MYSQL_USER=qforge
MYSQL_PASSWORD=qforge

REDIS_HOST=49.235.113.163
REDIS_PORT=16379

RABBITMQ_HOST=49.235.113.163
RABBITMQ_PORT=15674
RABBITMQ_USER=guest
RABBITMQ_PASSWORD=guest

NACOS_SERVER_ADDR=49.235.113.163:8848

# === 凭据 ===
JWT_SECRET=<your-secret>
ZHIPUAI_API_KEY=<your-key>
GLM_OCR_API_KEY=<your-key>

# === JVM 参数 ===
JAVA_OPTS_GATEWAY=-Xms128m -Xmx256m
JAVA_OPTS_AUTH=-Xms128m -Xmx256m
JAVA_OPTS_QUESTION_CORE=-Xms128m -Xmx256m
JAVA_OPTS_EXAM=-Xms128m -Xmx256m
JAVA_OPTS_EXAM_PARSE=-Xms128m -Xmx256m
JAVA_OPTS_QUESTION_BASKET=-Xms96m -Xmx192m
JAVA_OPTS_PERSIST=-Xms96m -Xmx192m
```

---

## 4. 迁移路径

### 阶段 1：验证 loopback IP 方案（最小改动）

在一台服务器上验证——不改 Docker 网络模式，只替换 hairpin NAT 为 loopback IP：

1. `ip addr add ${PUBLIC_IP}/32 dev lo`
2. 移除旧 iptables 规则（`iptables -t nat -F QFORGE_PUBLIC_HAIRPIN` 等）
3. 验证容器内 `curl http://${PUBLIC_IP}:8088/actuator/health` 可达
4. 验证跨机调用不受影响

> 如果验证通过，说明 loopback IP 方案在该云厂商结合 bridge 模式完全可用。  
> 如果失败（极少数情况），再考虑切 host 模式。

### 阶段 2：切换为 host 网络模式

在验证 loopback IP 可用后：

1. 编写新的 `docker-compose.remote.yml`（带 profiles + host 模式）
2. 编写 per-host env 文件
3. 在一台非关键机器上测试完整 deploy 流程
4. 逐台切换

### 阶段 3：清理

1. 删除或归档旧 `enable-public-hairpin-nat.sh`
2. 删除旧 `qforge-public-hairpin.service`
3. 更新 `docs/deploy/deploy01.md` 标记旧方案为历史记录

---

## 5. 需要注意的变化

### 5.1 Docker host 模式与 bridge 模式的差异

| 特性 | Bridge (当前) | Host (目标) |
|---|---|---|
| 端口映射 | `ports: "8080:8080"` | 不需要，进程直接绑定端口 |
| Docker DNS | `mysql`, `redis`, `qdrant` 等容器名可解析 | **不可用**，必须用 IP |
| 网络隔离 | 容器间默认隔离 | 全部共享宿主机网络 |
| 端口冲突 | 容器内端口互不冲突 | 宿主机端口全局唯一 |
| `depends_on` | 控制同 compose 启动顺序 | 仅影响同机，无跨机意义 |

### 5.2 需要调整的硬编码地址

以下 Docker DNS 引用需要替换为实际 IP：

| 当前值 | 使用位置 | Host 模式替换 |
|---|---|---|
| `QDRANT_HOST=qdrant` | gaokao-analysis-service | `127.0.0.1`（同机）或对端公网 IP |
| `EXPORT_SIDECAR_DIRECT_URL=http://export-sidecar:8092` | exam-service | `http://127.0.0.1:8092`（同机）或对端公网 IP |
| `OCR_SERVICE_BASE_URL=http://ocr-service:8090` | question-core-service | 建议改为完全走 Nacos Feign 发现，不再硬编码 |
| `MYSQL_HOST=mysql` | dev compose 中各服务 | 远程已是 IP，不受影响 |

### 5.3 前端容器 Nginx 端口

Host 模式下 Nginx 直接占用宿主机端口。当前 `web/Dockerfile` 和 `gaokao-web/Dockerfile` 中 Nginx 默认 listen 80，在 host 模式下会直接占用宿主机 80 端口。需要改为可配置端口或在 bridge 模式下单独处理前端容器（前端不需要 Nacos 注册，用 bridge + 端口映射即可）。

建议：**前端容器保持 bridge 模式**，只有后端 Java/Python 服务用 host 模式。前端容器仅需端口映射到宿主机，不涉及 Nacos 注册，bridge 模式完全够用。

### 5.4 安全注意事项

- Host 模式下服务直接暴露在宿主机网络上，确保云安全组只开放必要端口（8080 for gateway, 5173/5175 for web, 8848 for nacos）
- 中间件端口（MySQL 13306, Redis 16379 等）应仅对部署机器的公网 IP 开放，不对公网全部开放
- `loopback IP` 绑定不会影响安全组规则，因为安全组控制的是云 NAT 层，lo 接口的流量不经过安全组

---

## 6. 方案对比总结

| 维度 | 当前方案 (hairpin NAT) | 新方案 (host + loopback IP) |
|---|---|---|
| Nacos 注册 | 公网 IP ✓ | 公网 IP ✓ |
| 跨机访问 | ✓ | ✓ |
| 同机访问 | iptables DNAT 回环 | lo 本地投递 |
| 配置复杂度 | 高 (iptables 脚本 + systemd) | 低 (一条 ip addr + netplan) |
| Docker 重建影响 | 需重新执行脚本 | 无影响 |
| 多机管理 | 单一 env 文件手工复制 | per-host env + profiles |
| 调试难度 | 高 (iptables 规则链 + Docker 子网) | 低 (ip addr show lo) |
| 对本地开发的影响 | 无 | 无 (dev compose 不变) |

---

## 7. 风险评估

### 低风险
- Loopback IP 绑定是 Linux 标准能力，被广泛使用（LVS-DR、Kubernetes ExternalIP 等）
- Host 网络模式是 Docker 原生支持，无兼容性问题

### 需验证
- 具体云厂商（腾讯云轻量应用服务器）是否对 lo 上的外来 IP 有特殊限制——可通过阶段 1 快速验证
- 前端 Nginx 在 host 模式下的端口占用——建议前端保持 bridge 模式

### 回退方案
- 如 loopback IP 方案在特定云厂商不可用，可退回 host 模式 + 简化版 iptables OUTPUT 链 DNAT（只需一行 `iptables -t nat -A OUTPUT -d $PUBLIC_IP -j DNAT --to 127.0.0.1`，比当前方案简单得多）
