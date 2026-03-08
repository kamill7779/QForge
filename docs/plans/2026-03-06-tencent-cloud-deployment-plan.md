# QForge 腾讯云双机部署方案

> **日期**: 2026-03-06  
> **版本**: v1.0  
> **目标**: 以最低成本完成 QForge 生产环境部署，支撑中小规模用户使用

---

## 1. 资源清单

| 资源 | 规格 | 用途 | 月费参考 |
|------|------|------|---------|
| 轻量服务器 A（主服务节点） | 2C4G / 5Mbps / 60GB SSD | 基础设施 + 核心业务服务 | ~¥50-70 |
| 轻量服务器 B（网关 + 备用） | 2C4G / 5Mbps / 60GB SSD | 网关入口 + 备用服务 | ~¥50-70 |
| 腾讯云 TDSQL-C MySQL | 1C1G / 10GB Serverless | 数据库 | ~¥20-40 |
| 腾讯云 Redis | 256MB / Redis 7.0 | 缓存 + 会话状态 | ~¥15-25 |

**预估月费**: ¥135 — ¥205

---

## 2. 网络拓扑

```
                    ┌──────────────────┐
                    │   用户 (Electron)  │
                    └────────┬─────────┘
                             │ HTTPS (443)
                    ┌────────▼─────────┐
                    │   Server B       │
                    │  Nginx (反向代理)  │
                    │  gateway-service  │
                    │  ×2 实例          │
                    └────────┬─────────┘
                   内网 VPC  │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
┌─────────▼────┐   ┌────────▼────┐   ┌─────────▼────────┐
│  Server A    │   │ TDSQL-C     │   │ Cloud Redis      │
│  Nacos       │   │ MySQL       │   │ 256MB            │
│  RabbitMQ    │   │ 1C1G/10GB   │   │ Redis 7.0        │
│  question ×2 │   └─────────────┘   └──────────────────┘
│  auth ×1     │
│  persist ×1  │
│  ocr ×1      │
│  export ×1   │
└──────────────┘
```

### 2.1 VPC 内网互通

- 两台轻量服务器开启同地域 VPC 内网互联
- TDSQL-C / Cloud Redis 通过 VPC 内网访问，**不暴露公网**
- Server A 对外仅开放 Nacos Web（8848 仅内网）、RabbitMQ 管理（15672 仅内网）
- Server B 对外仅开放 80/443（Nginx 入口）

---

## 3. 服务分配

### 3.1 Server A — 主业务节点（2C4G）

| 服务 | 内部端口 | 实例数 | 预估内存 | 说明 |
|------|---------|--------|---------|------|
| Nacos | 8848 | 1 | ~400MB | 配置中心 + 服务发现（standalone） |
| RabbitMQ | 5672 / 15672 | 1 | ~250MB | 消息队列 |
| question-service | 8089 | 2 | 2×350MB | 核心业务（WebSocket + REST） |
| auth-service | 8088 | 1 | ~200MB | 登录认证 |
| persist-service | 8091 | 1 | ~200MB | MQ 消费者（异步持久化） |
| ocr-service | 8090 | 1 | ~350MB | OCR/AI 调用（CPU 绑定外部 API） |
| export-sidecar | 8092 | 1 | ~100MB | Python 导出服务 |
| **合计** | | **8** | **~2200MB** | 预留 ~1.8GB 给 OS + 缓冲 |

### 3.2 Server B — 网关入口（2C4G）

| 服务 | 内部端口 | 实例数 | 预估内存 | 说明 |
|------|---------|--------|---------|------|
| Nginx | 80 / 443 | 1 | ~50MB | TLS 终止 + 反向代理 |
| gateway-service | 8080 | 2 | 2×250MB | Spring Cloud Gateway（WebFlux） |
| question-service (备) | 8089 | 1 | ~350MB | 热备实例，分担查询流量 |
| **合计** | | **4** | **~900MB** | 预留充足，可酌情扩容 |

> **说明**: question-service 在 Server B 部署 1 个备用实例，通过 Nacos 自动负载均衡参与流量分配。

---

## 4. Docker Compose 配置

### 4.1 Server A — `docker-compose.server-a.yml`

```yaml
# ==============================================================
# Server A: 基础设施 + 核心业务
# 部署路径: /opt/qforge/docker-compose.yml
# ==============================================================
networks:
  qforge-net:
    driver: bridge

services:
  # ─── 基础设施 ───
  nacos:
    image: nacos/nacos-server:v2.4.1
    container_name: qforge-nacos
    restart: unless-stopped
    networks: [qforge-net]
    environment:
      MODE: standalone
      NACOS_AUTH_ENABLE: "false"
    volumes:
      - nacos-data:/home/nacos/data
    ports:
      - "127.0.0.1:8848:8848"    # 仅本机 + 内网访问
      - "9848:9848"                # gRPC 端口（服务发现需要）
    mem_limit: 512m

  rabbitmq:
    image: rabbitmq:3.13-management
    container_name: qforge-rabbitmq
    restart: unless-stopped
    networks: [qforge-net]
    environment:
      RABBITMQ_DEFAULT_USER: ${RABBITMQ_USER:-qforge}
      RABBITMQ_DEFAULT_PASS: ${RABBITMQ_PASSWORD:-qforge_mq_prod}
    volumes:
      - rabbitmq-data:/var/lib/rabbitmq
    ports:
      - "5672:5672"                # AMQP（内网访问）
      - "127.0.0.1:15672:15672"   # 管理 UI 仅本机
    mem_limit: 350m

  # ─── 业务服务 ───
  auth-service:
    image: ${DOCKER_REGISTRY:-}qforge/auth-service:${IMAGE_TAG:-latest}
    container_name: auth-service
    restart: unless-stopped
    networks: [qforge-net]
    logging: &logging
      driver: "json-file"
      options:
        max-size: "20m"
        max-file: "5"
    volumes:
      - ./logs:/app/logs
    environment: &common-env
      SPRING_PROFILES_ACTIVE: prod
      MYSQL_HOST: ${MYSQL_HOST}
      MYSQL_PORT: ${MYSQL_PORT:-3306}
      MYSQL_DB: ${MYSQL_DB:-qforge}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PORT: ${REDIS_PORT:-6379}
      REDIS_PASSWORD: ${REDIS_PASSWORD:-}
      RABBITMQ_HOST: rabbitmq
      RABBITMQ_PORT: 5672
      RABBITMQ_USER: ${RABBITMQ_USER:-qforge}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-qforge_mq_prod}
      NACOS_SERVER_ADDR: nacos:8848
      JWT_SECRET: ${JWT_SECRET}
      JAVA_TOOL_OPTIONS: >-
        -Xms128m -Xmx200m
        -XX:+UseG1GC
        -Djava.net.preferIPv4Stack=true
    mem_limit: 300m
    depends_on:
      nacos:
        condition: service_started
      rabbitmq:
        condition: service_started

  question-service:
    image: ${DOCKER_REGISTRY:-}qforge/question-service:${IMAGE_TAG:-latest}
    restart: unless-stopped
    networks: [qforge-net]
    logging: *logging
    volumes:
      - ./logs:/app/logs
    environment:
      <<: *common-env
      OCR_SERVICE_BASE_URL: http://ocr-service:8090
      JAVA_TOOL_OPTIONS: >-
        -Xms200m -Xmx350m
        -XX:+UseG1GC
        -Djava.net.preferIPv4Stack=true
    deploy:
      replicas: 2
    mem_limit: 450m
    depends_on:
      nacos:
        condition: service_started
      rabbitmq:
        condition: service_started

  persist-service:
    image: ${DOCKER_REGISTRY:-}qforge/persist-service:${IMAGE_TAG:-latest}
    container_name: persist-service
    restart: unless-stopped
    networks: [qforge-net]
    logging: *logging
    volumes:
      - ./logs:/app/logs
    environment:
      <<: *common-env
      JAVA_TOOL_OPTIONS: >-
        -Xms128m -Xmx200m
        -XX:+UseG1GC
        -Djava.net.preferIPv4Stack=true
    mem_limit: 300m
    depends_on:
      nacos:
        condition: service_started
      rabbitmq:
        condition: service_started

  ocr-service:
    image: ${DOCKER_REGISTRY:-}qforge/ocr-service:${IMAGE_TAG:-latest}
    container_name: ocr-service
    restart: unless-stopped
    networks: [qforge-net]
    logging: *logging
    volumes:
      - ./logs:/app/logs
    environment:
      <<: *common-env
      GLM_OCR_ENDPOINT: ${GLM_OCR_ENDPOINT:-https://api.z.ai/api/paas/v4/layout_parsing}
      GLM_OCR_MODEL: ${GLM_OCR_MODEL:-glm-ocr}
      GLM_OCR_API_KEY: ${GLM_OCR_API_KEY}
      ZHIPUAI_API_KEY: ${ZHIPUAI_API_KEY}
      ZHIPUAI_MODEL: ${ZHIPUAI_MODEL:-glm-5}
      STEMXML_MODEL: ${STEMXML_MODEL:-glm-4-0520}
      ANSWERXML_MODEL: ${ANSWERXML_MODEL:-glm-4-0520}
      EXAMPARSE_AI_MODEL: ${EXAMPARSE_AI_MODEL:-glm-4-plus}
      JAVA_TOOL_OPTIONS: >-
        -Xms200m -Xmx350m
        -XX:+UseG1GC
        -Djava.net.preferIPv4Stack=true
    mem_limit: 450m
    depends_on:
      nacos:
        condition: service_started
      rabbitmq:
        condition: service_started

  export-sidecar:
    image: ${DOCKER_REGISTRY:-}qforge/export-sidecar:${IMAGE_TAG:-latest}
    container_name: qforge-export-sidecar
    restart: unless-stopped
    networks: [qforge-net]
    logging: *logging
    environment:
      NACOS_SERVER_ADDR: nacos:8848
      SERVICE_PORT: 8092
    mem_limit: 150m
    depends_on:
      nacos:
        condition: service_started

volumes:
  nacos-data:
  rabbitmq-data:
```

### 4.2 Server B — `docker-compose.server-b.yml`

```yaml
# ==============================================================
# Server B: 网关入口 + 备用 question-service
# 部署路径: /opt/qforge/docker-compose.yml
# ==============================================================
networks:
  qforge-net:
    driver: bridge

services:
  gateway-service:
    image: ${DOCKER_REGISTRY:-}qforge/gateway-service:${IMAGE_TAG:-latest}
    restart: unless-stopped
    networks: [qforge-net]
    logging: &logging
      driver: "json-file"
      options:
        max-size: "20m"
        max-file: "5"
    volumes:
      - ./logs:/app/logs
    environment: &common-env
      SPRING_PROFILES_ACTIVE: prod
      REDIS_HOST: ${REDIS_HOST}
      REDIS_PORT: ${REDIS_PORT:-6379}
      REDIS_PASSWORD: ${REDIS_PASSWORD:-}
      RABBITMQ_HOST: ${SERVER_A_IP}
      RABBITMQ_PORT: 5672
      RABBITMQ_USER: ${RABBITMQ_USER:-qforge}
      RABBITMQ_PASSWORD: ${RABBITMQ_PASSWORD:-qforge_mq_prod}
      NACOS_SERVER_ADDR: ${SERVER_A_IP}:8848
      JWT_SECRET: ${JWT_SECRET}
      JAVA_TOOL_OPTIONS: >-
        -Xms128m -Xmx250m
        -XX:+UseG1GC
        -Djava.net.preferIPv4Stack=true
    deploy:
      replicas: 2
    ports:
      - "8080:8080"
    mem_limit: 350m

  question-service:
    image: ${DOCKER_REGISTRY:-}qforge/question-service:${IMAGE_TAG:-latest}
    container_name: question-service-standby
    restart: unless-stopped
    networks: [qforge-net]
    logging: *logging
    volumes:
      - ./logs:/app/logs
    environment:
      <<: *common-env
      MYSQL_HOST: ${MYSQL_HOST}
      MYSQL_PORT: ${MYSQL_PORT:-3306}
      MYSQL_DB: ${MYSQL_DB:-qforge}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD}
      OCR_SERVICE_BASE_URL: http://${SERVER_A_IP}:8090
      JAVA_TOOL_OPTIONS: >-
        -Xms200m -Xmx350m
        -XX:+UseG1GC
        -Djava.net.preferIPv4Stack=true
    mem_limit: 450m

  nginx:
    image: nginx:1.27-alpine
    container_name: qforge-nginx
    restart: unless-stopped
    networks: [qforge-net]
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/conf.d:/etc/nginx/conf.d:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - ./nginx/logs:/var/log/nginx
    mem_limit: 100m
    depends_on:
      - gateway-service
```

### 4.3 Nginx 配置 — `nginx/conf.d/qforge.conf`

```nginx
upstream gateway {
    # 两个 gateway-service 实例通过 Docker DNS
    server gateway-service:8080;
}

server {
    listen 80;
    server_name your-domain.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl http2;
    server_name your-domain.com;

    ssl_certificate     /etc/nginx/ssl/fullchain.pem;
    ssl_certificate_key /etc/nginx/ssl/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;

    client_max_body_size 100m;

    # REST API
    location /api/ {
        proxy_pass http://gateway;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
    }

    # WebSocket (试卷解析 / AI 推送)
    location /ws/ {
        proxy_pass http://gateway;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_read_timeout 600s;
    }
}
```

---

## 5. 环境变量 `.env` 文件

### 5.1 Server A — `/opt/qforge/.env`

```bash
# ── Docker 镜像 ──
DOCKER_REGISTRY=your-registry.com/
IMAGE_TAG=1.0.0

# ── 腾讯云 MySQL (TDSQL-C) ──
MYSQL_HOST=10.0.x.x          # 内网 IP
MYSQL_PORT=3306
MYSQL_DB=qforge
MYSQL_USER=qforge
MYSQL_PASSWORD=<强密码>

# ── 腾讯云 Redis ──
REDIS_HOST=10.0.x.x          # 内网 IP
REDIS_PORT=6379
REDIS_PASSWORD=<Redis密码>

# ── RabbitMQ ──
RABBITMQ_USER=qforge
RABBITMQ_PASSWORD=<MQ强密码>

# ── JWT ──
JWT_SECRET=<至少64字符的随机密钥>

# ── AI API Keys ──
GLM_OCR_API_KEY=<智谱OCR密钥>
ZHIPUAI_API_KEY=<智谱AI密钥>

# ── 模型选择 ──
GLM_OCR_ENDPOINT=https://api.z.ai/api/paas/v4/layout_parsing
GLM_OCR_MODEL=glm-ocr
ZHIPUAI_MODEL=glm-5
STEMXML_MODEL=glm-4-0520
ANSWERXML_MODEL=glm-4-0520
EXAMPARSE_AI_MODEL=glm-4-plus
```

### 5.2 Server B — `/opt/qforge/.env`

```bash
# ── Docker 镜像 ──
DOCKER_REGISTRY=your-registry.com/
IMAGE_TAG=1.0.0

# ── Server A 内网 IP ──
SERVER_A_IP=10.0.x.x

# ── 腾讯云 MySQL (TDSQL-C) ──
MYSQL_HOST=10.0.x.x
MYSQL_PORT=3306
MYSQL_DB=qforge
MYSQL_USER=qforge
MYSQL_PASSWORD=<强密码>

# ── 腾讯云 Redis ──
REDIS_HOST=10.0.x.x
REDIS_PORT=6379
REDIS_PASSWORD=<Redis密码>

# ── RabbitMQ (连 Server A) ──
RABBITMQ_USER=qforge
RABBITMQ_PASSWORD=<MQ强密码>

# ── JWT (两台服务器必须一致) ──
JWT_SECRET=<至少64字符的随机密钥>
```

---

## 6. 镜像构建与推送

### 6.1 本地构建（开发机）

```bash
# 项目根目录 backend/
cd backend

# 构建 5 个 Java 服务镜像
for svc in auth-service gateway-service question-service persist-service ocr-service; do
  docker build --build-arg SERVICE_NAME=$svc -t qforge/$svc:1.0.0 .
done

# 构建 export-sidecar
docker build -t qforge/export-sidecar:1.0.0 ./export-sidecar
```

### 6.2 推送到镜像仓库

```bash
# 方案 A：腾讯云容器镜像服务 (TCR Personal)
REGISTRY=ccr.ccs.tencentyun.com/qforge

for svc in auth-service gateway-service question-service persist-service ocr-service export-sidecar; do
  docker tag qforge/$svc:1.0.0 $REGISTRY/$svc:1.0.0
  docker push $REGISTRY/$svc:1.0.0
done

# 方案 B：直接 docker save / scp 传输（无仓库场景）
for svc in auth-service gateway-service question-service persist-service ocr-service export-sidecar; do
  docker save qforge/$svc:1.0.0 | gzip > ${svc}-1.0.0.tar.gz
done
# scp 到目标服务器后 docker load
```

---

## 7. 部署步骤

### 7.1 前置准备

| 步骤 | 操作 | 说明 |
|------|------|------|
| 1 | 购买 2 台腾讯云轻量服务器 | 同地域，确保内网互通 |
| 2 | 购买 TDSQL-C Serverless MySQL | 同 VPC，白名单添加两台服务器内网 IP |
| 3 | 购买 Cloud Redis 256MB | 同 VPC，白名单添加两台服务器内网 IP |
| 4 | 两台服务器安装 Docker + Docker Compose | 参考 §7.2 |
| 5 | TDSQL-C 初始化 Schema | 导入 `sql/init-schema.sql` |
| 6 | 配置防火墙规则 | 参考 §7.3 |

### 7.2 安装 Docker（两台服务器）

```bash
# Ubuntu / Debian
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
sudo systemctl enable docker

# 验证
docker --version
docker compose version
```

### 7.3 防火墙规则

**Server A — 安全组**

| 方向 | 端口 | 来源 | 说明 |
|------|------|------|------|
| 入站 | 22 | 管理 IP | SSH |
| 入站 | 8848,9848 | Server B 内网 IP | Nacos（服务发现） |
| 入站 | 5672 | Server B 内网 IP | RabbitMQ AMQP |
| 入站 | 8089,8090,8091 | Server B 内网 IP | 业务服务内网调用 |

**Server B — 安全组**

| 方向 | 端口 | 来源 | 说明 |
|------|------|------|------|
| 入站 | 22 | 管理 IP | SSH |
| 入站 | 80 | 0.0.0.0/0 | HTTP → HTTPS 重定向 |
| 入站 | 443 | 0.0.0.0/0 | HTTPS 入口 |

### 7.4 部署 Server A

```bash
# 创建部署目录
sudo mkdir -p /opt/qforge && cd /opt/qforge

# 上传文件 (scp 或 git clone)
# - docker-compose.yml  (即 docker-compose.server-a.yml)
# - .env
# - 镜像 tar.gz (如果不用仓库)

# 加载镜像 (方案 B 无仓库场景)
for f in *-1.0.0.tar.gz; do docker load < "$f"; done

# 启动
docker compose up -d

# 验证
docker compose ps
docker compose logs -f --tail=50
```

### 7.5 部署 Server B

```bash
sudo mkdir -p /opt/qforge/nginx/{conf.d,ssl,logs} && cd /opt/qforge

# 上传文件
# - docker-compose.yml  (即 docker-compose.server-b.yml)
# - .env
# - nginx/conf.d/qforge.conf
# - nginx/ssl/fullchain.pem + privkey.pem

# 加载镜像
for f in *-1.0.0.tar.gz; do docker load < "$f"; done

# 启动
docker compose up -d

# 验证
docker compose ps
curl -k https://localhost/api/auth/health
```

### 7.6 初始化 Nacos 配置

部署完成后，通过 Nacos API 上传生产配置：

```bash
# 在 Server A 执行
NACOS=http://127.0.0.1:8848

for f in auth-service.yml gateway-service.yml question-service.yml persist-service.yml ocr-service.yml; do
  curl -s -X POST "$NACOS/nacos/v1/cs/configs" \
    --data-urlencode "dataId=$f" \
    --data-urlencode "group=DEFAULT_GROUP" \
    --data-urlencode "type=yaml" \
    --data-urlencode "content@/opt/qforge/configs/$f"
  echo " → $f uploaded"
done
```

---

## 8. 数据库初始化

```bash
# 连接 TDSQL-C MySQL 执行初始化脚本
mysql -h <TDSQL-C内网IP> -P 3306 -u qforge -p qforge < sql/init-schema.sql

# 验证
mysql -h <TDSQL-C内网IP> -u qforge -p -e "USE qforge; SHOW TABLES;"
```

数据库共 15 张表:
`user_account`, `q_question`, `q_tag_category`, `q_tag`, `q_answer`,
`q_question_asset`, `q_answer_asset`, `q_question_tag_rel`, `q_question_ocr_task`,
`q_ocr_task`, `q_ai_analysis_task`, `q_question_ai_task`, `q_exam_parse_task`,
`q_exam_parse_source_file`, `q_exam_parse_question`

---

## 9. 健康检查与验证

### 9.1 逐层验证

```bash
# 1. 基础设施
curl http://127.0.0.1:8848/nacos/   # Nacos Web (Server A)
curl http://127.0.0.1:15672/         # RabbitMQ UI (Server A)

# 2. 服务注册
curl -s "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=auth-service" | python3 -m json.tool

# 3. 网关路由 (Server B)
curl -k https://localhost/api/auth/health

# 4. 端到端测试
curl -k -X POST https://your-domain.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
```

### 9.2 预期服务注册列表

| 服务名 | 实例数 | 分布 |
|--------|--------|------|
| auth-service | 1 | Server A |
| gateway-service | 2 | Server B |
| question-service | 3 | Server A ×2 + Server B ×1 |
| persist-service | 1 | Server A |
| ocr-service | 1 | Server A |
| export-sidecar | 1 | Server A |

---

## 10. 运维手册

### 10.1 日志查看

```bash
# 查看特定服务日志
docker compose logs -f auth-service --tail=100

# 查看所有服务日志
docker compose logs -f --tail=50

# 日志文件位置
ls /opt/qforge/logs/
```

### 10.2 滚动更新

```bash
# 1. 构建新版本镜像 (开发机)
docker build --build-arg SERVICE_NAME=question-service -t qforge/question-service:1.0.1 .

# 2. 推送/传输到服务器

# 3. 更新 .env 中 IMAGE_TAG
sed -i 's/IMAGE_TAG=.*/IMAGE_TAG=1.0.1/' .env

# 4. 滚动重启 (零停机 — 多实例服务)
docker compose up -d --no-deps question-service

# 5. 验证
docker compose ps
curl -s "http://127.0.0.1:8848/nacos/v1/ns/instance/list?serviceName=question-service"
```

### 10.3 Nacos 热配置变更

```bash
# 修改某个配置项（无需重启服务）
curl -X POST "http://127.0.0.1:8848/nacos/v1/cs/configs" \
  --data-urlencode "dataId=question-service.yml" \
  --data-urlencode "group=DEFAULT_GROUP" \
  --data-urlencode "type=yaml" \
  --data-urlencode "content@/opt/qforge/configs/question-service.yml"
```

支持热更新的配置项（`@ConfigurationProperties` + `@RefreshScope`）：
- `qforge.business.*`（question-service）
- `qforge.ocr.*`（ocr-service）
- `spring.rabbitmq.listener.*`（persist-service）

### 10.4 备份策略

| 数据 | 策略 | 说明 |
|------|------|------|
| TDSQL-C MySQL | 自动备份（腾讯云默认 7 天） | 无需额外操作 |
| Cloud Redis | AOF 持久化（腾讯云默认） | 仅存热数据，丢失无影响 |
| Nacos 配置 | `nacos-data` volume + Git 版本管理 | `backend/configs/` 已纳入 Git |
| RabbitMQ | `rabbitmq-data` volume | 消息队列，重启不丢消息 |

### 10.5 磁盘监控

```bash
# 定期检查磁盘使用
df -h
docker system df

# 清理无用镜像和构建缓存
docker system prune -f
docker image prune -a --filter "until=720h"   # 清理 30 天前的旧镜像
```

---

## 11. 扩容路径

当用户量增长时的扩容建议：

| 阶段 | 触发条件 | 操作 |
|------|---------|------|
| Phase 1 | CPU 持续 >70% | TDSQL-C 升配 2C2G；Redis 升 1GB |
| Phase 2 | 并发 >50 用户 | 增加第 3 台轻量服务器，部署额外 question-service + ocr-service |
| Phase 3 | 并发 >200 用户 | 迁移至 CVM/TKE，Nacos 集群模式，MySQL 读写分离 |

---

## 12. 检查清单

部署前务必完成以下确认：

- [ ] 两台服务器内网互通测试 (`ping`)
- [ ] TDSQL-C MySQL 从两台服务器均可连通
- [ ] Cloud Redis 从两台服务器均可连通
- [ ] `.env` 中所有密码已替换为强密码（不使用默认值）
- [ ] `JWT_SECRET` 在两台服务器上一致且 ≥64 字符
- [ ] SSL 证书已上传到 Server B `/opt/qforge/nginx/ssl/`
- [ ] DNS 已解析到 Server B 公网 IP
- [ ] `init-schema.sql` 已在 TDSQL-C 中执行
- [ ] 所有 6 个 Docker 镜像已推送/加载到两台服务器
- [ ] Nacos 配置已通过 API 上传
- [ ] 防火墙规则已按 §7.3 配置
- [ ] 基本功能端到端测试通过
