# QForge Final Deployment Plan

更新时间：2026-03-11

## 目标

本文给出当前仓库推荐的最终远程部署方案，前提如下：

- 宿主机之间通过云内网互通。
- 所有 Spring 服务向 Nacos 注册宿主机内网 IP。
- 远程部署继续使用 [backend/deploy/docker-compose.remote.yml](/home/ubuntu/QForge/backend/deploy/docker-compose.remote.yml)。
- 不再使用公网 loopback / hairpin NAT 方案。

本文对应的默认硬件形态是：

- 2 台 4C4G
- 3 台 2C4G，其中 2 台是新增业务机
- 1 台 2C8G500GB，作为数据面机器

如果实际采购和本文不完全一致，优先保留本文的角色边界，不要重新把控制面、数据面、AI 面混放。

## 最终拓扑

### 节点 1：接入层

- 规格：4C4G
- 推荐主机名：edge-01
- 职责：前端接入、网关入口

部署服务：

- `gateway-service`
- `web-exam`
- `gaokao-web`
- 第三个静态前端容器或站点

说明：

- 这一台尽量只承担入口层，不再叠加 `question-core-service`、`exam-service`、`ocr-service`、`gaokao-analysis-service`。
- 当前仓库内已有 2 个 Web 前端容器；如果后续新增第 3 个前端，默认与这 2 个同机。
- `client` 是 Electron 桌面端，不属于远程常驻服务。

### 节点 2：核心业务层

- 规格：4C4G
- 推荐主机名：core-01
- 职责：正式题库、试卷、解析、导出主链路

部署服务：

- `question-core-service`
- `exam-service`
- `exam-parse-service`
- `gaokao-corpus-service`
- `export-sidecar`

说明：

- 这是主业务计算面，优先保证稳定。
- `exam-service` 和 `export-sidecar` 同机，导出链路最短，`EXAM_EXPORT_SIDECAR_BASE_URL` 可直接指向本机。

### 节点 3：控制面 + 轻业务层

- 规格：2C4G
- 推荐主机名：control-01
- 职责：服务发现、MQ、轻量业务服务

部署服务：

- `nacos`
- `rabbitmq`
- `auth-service`
- `question-basket-service`
- `persist-service`

说明：

- 不要在这台机器上叠加 `ocr-service`、`gaokao-analysis-service`、`qdrant`。
- `question-basket-service` 和 `persist-service` 负载相对轻，适合与控制面同机。

### 节点 4：数据面

- 规格：2C8G500GB
- 推荐主机名：data-01
- 职责：数据库、缓存、备份、归档

部署服务：

- `mysql`
- `redis`

说明：

- 这台机器应保持纯数据面，不要顺手再塞 `nacos`、`rabbitmq`、`qdrant`、`ocr-service` 或 `gaokao-analysis-service`。
- 升级带来的主要收益应该用在 MySQL buffer、Redis 容量、页缓存、备份和日志归档上。

### 节点 5：OCR 节点

- 规格：2C4G
- 推荐主机名：ai-ocr-01
- 职责：OCR、拆题、AI 初步处理

部署服务：

- `ocr-service`

说明：

- 这一台机器不混放控制面服务。
- OCR 链路 CPU 和外部调用波动较大，独立一台的收益高于和分析链路混放。

### 节点 6：分析节点

- 规格：2C4G
- 推荐主机名：ai-analysis-01
- 职责：高考向量索引、相似题分析、Qdrant

部署服务：

- `gaokao-analysis-service`
- `qdrant`

说明：

- `gaokao-analysis-service` 与 `qdrant` 同机部署，优先走本机回环。
- 不要把控制面或 OCR 服务再叠加到这一台。

## 角色边界

必须尽量守住以下边界：

- 接入层只做网关和前端。
- 核心业务层只承接正式题库、试卷、导出、解析主链路。
- 控制面只做 Nacos、RabbitMQ 和轻业务。
- 数据面只做 MySQL、Redis、备份、归档。
- AI 面拆成 2 台：OCR 节点 和 分析节点。

买了两台 2C4G 以后，不要又把它们重新混成“大杂烩节点”。

## 服务与节点映射

| 服务 | 推荐节点 | 备注 |
| --- | --- | --- |
| `mysql` | `data-01` | 单实例，纯数据面 |
| `redis` | `data-01` | 单实例，纯数据面 |
| `nacos` | `control-01` | 当前仍按 standalone 运行 |
| `rabbitmq` | `control-01` | 单实例，当前不做集群 |
| `auth-service` | `control-01` | 轻量无状态服务 |
| `gateway-service` | `edge-01` | 统一入口 |
| `question-core-service` | `core-01` | 正式题库主数据 |
| `question-basket-service` | `control-01` | 轻量 CRUD + 组卷状态 |
| `exam-service` | `core-01` | 试卷与导出编排 |
| `exam-parse-service` | `core-01` | 解析任务主链路 |
| `persist-service` | `control-01` | MQ 写回消费者 |
| `ocr-service` | `ai-ocr-01` | 单独一台 |
| `gaokao-corpus-service` | `core-01` | 高考语料业务面 |
| `gaokao-analysis-service` | `ai-analysis-01` | 与 `qdrant` 同机 |
| `qdrant` | `ai-analysis-01` | 与分析服务同机 |
| `export-sidecar` | `core-01` | 与 `exam-service` 同机 |
| `web-exam` | `edge-01` | Web 前端 |
| `gaokao-web` | `edge-01` | 高考前端 |

## 建议的 env 使用方式

当前 compose 已按 profile 拆分，但推荐拓扑不是简单的 profile 对应一台机器，所以应通过“复制模板 + 指定服务子集”来部署。

### edge-01

- 基于模板：`hosts/core-frontend.env.example`
- 推荐 profile：`core,frontend`
- 启动服务：`gateway-service web-exam gaokao-web`
- 如果存在第 3 个前端：与上述前端同机部署

### core-01

- 基于模板：`hosts/core-frontend.env.example`
- 推荐 profile：`core,ai,sidecar`
- 启动服务：`question-core-service exam-service exam-parse-service gaokao-corpus-service export-sidecar`

### control-01

- 基于模板：`hosts/infra.env.example` 或复制后自定义
- 推荐 profile：`infra,core`
- 启动服务：`nacos rabbitmq auth-service question-basket-service persist-service`

### data-01

- 基于模板：`hosts/infra.env.example`
- 推荐 profile：`infra`
- 启动服务：`mysql redis`

### ai-ocr-01

- 基于模板：`hosts/ai.env.example`
- 推荐 profile：`ai`
- 启动服务：`ocr-service`

### ai-analysis-01

- 基于模板：`hosts/ai.env.example`
- 推荐 profile：`ai`
- 启动服务：`gaokao-analysis-service qdrant`

## JVM 与容器上限建议

下表是推荐覆盖值，优先以 host env 中的 `JAVA_OPTS_*` 覆盖默认值。

| 服务 | 建议 `JAVA_OPTS` | 建议容器上限 | 说明 |
| --- | --- | --- | --- |
| `auth-service` | `-Xms128m -Xmx192m` | `320m-384m` | 轻量无状态 |
| `gateway-service` | `-Xms192m -Xmx320m` | `448m-512m` | 入口层 |
| `question-core-service` | `-Xms256m -Xmx512m` | `768m` | 读写主链路 |
| `question-basket-service` | `-Xms128m -Xmx256m` | `384m` | 轻量业务 |
| `exam-service` | `-Xms256m -Xmx384m` | `640m-768m` | 导出和详情聚合 |
| `exam-parse-service` | `-Xms192m -Xmx320m` | `512m-640m` | 解析任务编排 |
| `persist-service` | `-Xms128m -Xmx192m` | `320m-384m` | MQ 消费写回 |
| `ocr-service` | `-Xms256m -Xmx640m -Djava.net.preferIPv4Stack=true` | `1024m-1280m` | CPU 和内存双敏感 |
| `gaokao-corpus-service` | `-Xms192m -Xmx384m` | `640m-768m` | 高考业务面 |
| `gaokao-analysis-service` | `-Xms384m -Xmx768m` | `1280m-1536m` | 向量索引和分析 |

非 JVM 服务建议：

- `mysql`：保留 3G 左右内存空间给 InnoDB buffer、连接和页缓存。
- `redis`：建议预留 `512m-1g` 可控空间，明确淘汰策略。
- `nacos`：`512m-640m`。
- `rabbitmq`：`384m-512m`。
- `qdrant`：`640m-768m`。
- `export-sidecar`：`384m-512m`。

## 部署顺序

建议按下面顺序启动，降低级联失败概率：

1. `data-01`：`mysql redis`
2. `control-01`：`nacos rabbitmq auth-service question-basket-service persist-service`
3. `core-01`：`question-core-service exam-service exam-parse-service gaokao-corpus-service export-sidecar`
4. `ai-analysis-01`：`gaokao-analysis-service qdrant`
5. `ai-ocr-01`：`ocr-service`
6. `edge-01`：`gateway-service web-exam gaokao-web`

## 适合加副本的服务

当前 compose 采用 `host` 网络、固定端口和固定 `container_name`，因此同一台机器上不适合直接起同服务多副本。高峰期多开应理解为：

- 在另一台空闲宿主机上再部署一个实例
- 保持相同的 Nacos 服务名和端口
- 让网关 / Feign / MQ 自然分流

### 第一优先级

- `gateway-service`
- `question-core-service`
- `exam-service`
- `export-sidecar`
- `ocr-service`
- `gaokao-analysis-service`
- `persist-service`

原因：

- 它们要么是入口层无状态服务，要么是内部无状态编排服务，要么是 MQ 消费者，横向扩更有收益。

### 第二优先级

- `auth-service`
- `question-basket-service`

原因：

- 这两者也能多开，但当前不是第一瓶颈。

### 谨慎多开

- `exam-parse-service`
- `gaokao-corpus-service`

原因：

- 这两条链路涉及上传文件、任务态和较重业务流程；在没有补充共享文件路径、对象存储或更完整的无状态验证前，不建议把它们当第一批扩容对象。

### 当前不建议作为“高峰镜像”直接多开

- `mysql`
- `redis`
- `nacos`
- `rabbitmq`
- `qdrant`

原因：

- 它们属于基础设施或状态组件，当前仓库并没有对应的高可用编排与数据同步方案。

## 空闲资源的优先使用顺序

如果线上还有空闲机器资源，建议按下面顺序加副本：

1. 再加一个 `gateway-service`
2. 再加一个 `export-sidecar`
3. 视访问热点补 `question-core-service` 或 `exam-service`
4. OCR 高峰明显时补 `ocr-service`
5. 高考索引积压明显时补 `gaokao-analysis-service` 或 `persist-service`

## 不要做的事

- 不要把 `mysql`、`redis` 和 Java 业务服务重新混住。
- 不要把 `ocr-service`、`gaokao-analysis-service`、`qdrant` 和 `nacos`、`rabbitmq` 混到同一台 2C4G。
- 不要因为存储机升级到 `2C8G500GB` 就把它当成“大杂烩节点”。
- 不要默认同一台宿主机上用当前 compose 直接 scale 同服务多个副本。

## 最终建议摘要

最终推荐方案是：

- 接入层 1 台 4C4G
- 核心业务层 1 台 4C4G
- 控制面 1 台 2C4G
- 数据面 1 台 2C8G500GB
- OCR 节点 1 台 2C4G
- 分析节点 1 台 2C4G

如果预算不足，优先保住“数据面独立”和“AI 面拆成 2 台”，不要先牺牲这两条边界。