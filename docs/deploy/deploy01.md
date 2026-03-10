# QForge 本次部署故障复盘与完整修复方案

## 1. 背景

本次部署的目标是让当前机器对外提供以下能力：

- qforge.cn 和 www.qforge.cn 使用 HTTPS 正常访问
- QForge Web 前端通过 Nginx 对外提供服务
- gateway-service 和 auth-service 运行在 Docker 中
- 服务通过 Nacos 使用公网地址注册，供其他服务器访问
- 同一台机器上的容器在通过 Nacos 拿到公网地址后，也能正常回调本机服务

最终采用的运行模型是：

- 对外暴露地址保持为公网 IP 111.229.17.125
- 服务在 Nacos 中注册公网 IP，而不是 Docker 内部 172.* 地址
- 宿主机增加公网 IP 回环 DNAT，使同机容器访问 111.229.17.125 时自动回流到宿主机私网地址 10.0.0.15


## 2. 本次出现的问题

部署过程中先后出现了以下问题：

### 2.1 最早的 502

外部客户端访问登录接口时返回 502，但服务器本机访问链路正常。

根因是 Nginx 默认站点仍然把部分流量代理到历史遗留服务 127.0.0.1:3000，而不是当前 QForge 的 gateway。

### 2.2 WebSocket 异常

登录恢复后，前端 WebSocket 显示未连接。

根因不是 Nginx，而是 gateway 的下游 question-core-service 不可用，导致 gateway 路由到 ws://question-core-service 时找不到可用实例。

### 2.3 Nacos 注册成 Docker 内网地址

auth 和 gateway 默认在 Nacos 注册成 172.* 容器地址。这种地址只在当前 Docker 网络内可达，对跨机器调用没有意义。

如果其他服务器通过 Nacos 获取到 172.* 地址，将无法访问本机服务。

### 2.4 改成公网注册后，本机容器自调用失败

把 auth 和 gateway 改成公网地址注册后，跨机器访问方向是对的，但同机容器通过 Nacos 获取到 111.229.17.125:8088 这类地址再回调本机时失败。

典型报错如下：

- gateway 调用 auth 时出现 connection refused
- 外部登录接口从 401 或正常业务响应退化成 503/500

### 2.5 公网回环 NAT 规则互相冲突

后续虽然增加了 hairpin NAT，但系统里同时存在两套不同逻辑的规则：

- 旧规则：把公网 IP 直接 DNAT 到某个容器 172.* 地址
- 新规则：把公网 IP DNAT 到宿主机私网地址 10.0.0.15

旧规则残留后，会优先生效或与新规则混用，导致 gateway 从容器访问 111.229.17.125:8088 时被错误转发到失效的旧容器地址，出现 connection refused。

### 2.6 Nacos 中存在过期或外部实例

排查过程中还发现 Nacos 中存在额外实例，例如其他公网 IP 的 gateway 实例。这类实例不是当前节点本地规则能自动消掉的，通常代表另一台机器仍在持续心跳。


## 3. 根因总结

本次问题不是单点故障，而是多个部署层面的问题叠加：

### 3.1 流量入口层

Nginx 默认站点与新业务站点没有完全统一，仍然残留旧的 127.0.0.1:3000 代理逻辑。

### 3.2 服务发现层

默认注册行为使用 Docker 容器地址，不适合生产中的跨机器调用。

### 3.3 同机回流层

服务注册为公网 IP 后，如果宿主机没有做公网回环 DNAT，同机容器拿到公网地址后无法正确访问本机服务。

### 3.4 规则治理层

历史脚本和新脚本并存，iptables 中残留了旧规则，导致实际转发路径不可预测。

### 3.5 依赖关系层

gateway 的部分 API 和 WebSocket 依赖下游 question-core-service，如果核心下游未启动或不可达，表面上会表现为前端连接失败。


## 4. 最终正确架构

最终确定的可用架构如下：

### 4.1 对外入口

- Nginx 对外监听 80 和 443
- HTTPS 使用 qforge.cn 证书
- / 转发到 Web 前端
- /api/ 和 /ws/ 转发到 gateway-service

### 4.2 服务运行

- qforge-web-exam 暴露到宿主机 5174
- gateway-service 暴露到宿主机 8080
- auth-service 暴露到宿主机 8088

### 4.3 服务注册

- auth-service 在 Nacos 中注册为 111.229.17.125:8088
- gateway-service 在 Nacos 中注册为 111.229.17.125:8080

### 4.4 同机容器回流

当 Docker 容器访问以下地址时：

- 111.229.17.125:8080
- 111.229.17.125:8088
- 111.229.17.125:5174
- 以及其他预留业务端口

iptables 自动将其 DNAT 到：

- 10.0.0.15:8080
- 10.0.0.15:8088
- 10.0.0.15:5174

这样可以同时满足两点：

- 其他服务器看到的是公网地址，能够正常访问
- 本机容器看到的也是公网地址，但会被宿主机回流到本机私网，不会出公网再失败


## 5. 完整修复方案

### 5.1 修复 Nginx 入口

修复目标：统一域名访问和 IP 访问的默认代理目标。

已调整内容：

- 将 qforge.cn 和 www.qforge.cn 的 HTTPS 流量代理到当前 QForge 服务
- 将默认 80 站点中的 /、/api/、/ws/ 也改为当前 QForge 路由
- 移除对旧 demo 服务 127.0.0.1:3000 的依赖

当前生效逻辑：

- / -> 127.0.0.1:5174
- /api/ -> 127.0.0.1:8080
- /ws/ -> 127.0.0.1:8080

对应文件：

- /home/ubuntu/hello-proxy/default.nginx
- /etc/nginx/sites-available/default

### 5.2 修复服务注册地址

修复目标：避免服务注册成 172.* Docker 内网地址。

已调整内容：

- 为 auth-service 显式设置公网注册 IP 和端口
- 为 gateway-service 显式设置公网注册 IP 和端口

关键环境变量：

```yaml
SPRING_CLOUD_NACOS_DISCOVERY_IP=111.229.17.125
SPRING_CLOUD_NACOS_DISCOVERY_PORT=8088
```

以及：

```yaml
SPRING_CLOUD_NACOS_DISCOVERY_IP=111.229.17.125
SPRING_CLOUD_NACOS_DISCOVERY_PORT=8080
```

对应文件：

- /home/ubuntu/QForge-main/backend/deploy/docker-compose.remote.yml

### 5.3 修复公网回环 NAT

修复目标：让本机容器访问本机公网地址时，自动回流到宿主机私网地址。

最终使用的文件如下：

- /home/ubuntu/QForge-main/backend/deploy/remote-stack.env
- /home/ubuntu/QForge-main/backend/deploy/enable-public-hairpin-nat.sh
- /home/ubuntu/QForge-main/backend/deploy/qforge-public-hairpin.service

核心思路：

1. 读取公网 IP、宿主机私网 IP、需要回流的端口列表
2. 自动识别 Docker bridge 子网
3. 为这些子网访问公网 IP 的指定端口添加 DNAT
4. 将目标统一改写为宿主机私网地址 10.0.0.15
5. 添加 POSTROUTING MASQUERADE，确保回程正常
6. 规则必须只匹配 Docker 子网访问本机公网 IP 的业务端口，不能泛化到所有入站流量，否则会误伤 SSH

### 5.4 清理旧的冲突规则

这是本次最终修复里最关键的一步。

仅仅增加新 NAT 规则还不够，因为系统里已经存在旧脚本写入的直连容器规则，例如：

- 111.229.17.125:8088 -> 172.18.x.x:8088

这些旧规则一旦命中，就会把流量转发到过期或错误的容器，导致 gateway 调用 auth 失败。

因此最终脚本必须具备以下能力：

- 先删除所有目标为 172.* 容器地址的旧 DNAT 规则
- 再重新生成统一的目标为 10.0.0.6 的新规则
- 确保脚本可重复执行且结果一致

这也是为什么最终修改了下列文件：

- /home/ubuntu/QForge-main/backend/deploy/enable-public-hairpin-nat.sh

### 5.5 处理 gateway 的下游依赖

如果需要前端完整使用题目相关接口或 WebSocket，必须保证 gateway 所依赖的下游服务也可用。

本次已确认：

- question-core-service 缺失时，gateway 的相关能力会异常

但在当前这台机器上，实际运行策略已经调整为：

- auth-service 仍然关闭
- gateway-service 保持运行
- question-core-service、question-basket-service、exam-service、exam-parse-service、ocr-service、persist-service、gaokao-corpus-service、gaokao-analysis-service、export-sidecar 已启用

这样做的目的，是直接恢复后端微服务之间经由 Nacos 的相互调用，优先解决 500 问题。


## 6. 最终验证结果

以下链路已经验证通过：

### 6.1 容器内回流验证

在 gateway-service 容器内执行：

```bash
curl -i http://111.229.17.125:8088/actuator/health
```

结果为 HTTP 200，说明 gateway 容器访问 auth 的公网地址时，hairpin NAT 已正常生效。

### 6.2 外部登录链路验证

执行：

```bash
curl -sk -i -H 'Content-Type: application/json' \
  -X POST https://www.qforge.cn/api/auth/login \
  -d '{"username":"invalid","password":"invalid"}'
```

返回 HTTP 401，而不是 503。

这说明：

- Nginx 正常
- gateway 正常
- gateway 到 auth 的调用正常
- 认证逻辑已经进入业务层，而不是卡在网络层

### 6.3 Nacos 注册验证

已确认：

- auth-service 注册为 111.229.17.125:8088
- gateway-service 注册为 111.229.17.125:8080

说明公网注册方案已经生效。


## 7. 当前仍需注意的事项

### 7.1 Nacos 中的额外实例不一定来自本机

如果 Nacos 中仍出现除 111.229.17.125 之外的其他公网实例，即使手动删除后又重新出现，通常说明有另一台机器仍在持续注册和发送心跳。

这类问题需要到对应机器上处理，而不是继续修改本机 iptables。

### 7.2 不要再使用旧脚本直接写 172.* DNAT

历史脚本会把公网端口直接映射到具体容器 IP，这种做法非常脆弱，容器一旦重建 IP 就变化，规则立即失效。

后续只能保留统一的宿主机私网回流方案，不要再恢复旧规则。

### 7.3 question-core-service 是否运行，要按业务需求决定

如果只要求登录和基础网关能力，可以不启动 question-core-service。

如果要恢复相关 WebSocket 和题目业务链路，则必须启动 question-core-service，并确保它也采用正确的公网注册和回流策略。


## 8. 后续标准部署建议

为了避免未来再次出现相同问题，建议以后严格按照下面的顺序部署：

### 8.1 基础设施确认

确认以下服务对宿主机可用：

- MySQL
- Redis
- RabbitMQ
- Nacos
- Nginx

### 8.2 应用配置确认

确认 docker-compose 中：

- auth-service 和 gateway-service 使用外部基础设施地址
- auth-service 和 gateway-service 设置了公网注册 IP/PORT
- Web 容器端口映射正确

### 8.3 先应用回环 NAT

在启动核心业务容器前先执行：

```bash
cd /home/ubuntu/QForge-main/backend/deploy
sudo ./enable-public-hairpin-nat.sh
```

并确保 systemd 服务已启用：

```bash
sudo systemctl enable --now qforge-public-hairpin.service
```

### 8.4 再启动应用服务

例如：

```bash
cd /home/ubuntu/QForge-main/backend/deploy
sudo docker compose --env-file remote-stack.env -f docker-compose.remote.yml up -d --build \
  question-core-service question-basket-service exam-service exam-parse-service \
  ocr-service persist-service gaokao-corpus-service gaokao-analysis-service export-sidecar gateway-service
```

### 8.5 最后做四类验证

必须至少验证以下四项：

1. Nginx 入口是否正确指向当前 Web 和 gateway
2. Nacos 中注册的是否为公网地址而不是 172.*
3. 任一业务容器内访问本机公网业务地址是否能正常回流
4. 微服务间通过 Nacos 的调用是否从 500 恢复为业务响应


## 9. 推荐排查命令

### 9.1 查看服务状态

```bash
sudo docker ps --format 'table {{.Names}}\t{{.Status}}'
```

### 9.2 查看 auth 日志

```bash
sudo docker logs --tail 120 auth-service
```

### 9.3 查看 gateway 日志

```bash
sudo docker logs --tail 160 gateway-service
```

### 9.4 查看 Nacos 注册

```bash
curl -s 'http://127.0.0.1:8848/nacos/v2/ns/instance/list?serviceName=auth-service&groupName=DEFAULT_GROUP&namespaceId=public'
curl -s 'http://127.0.0.1:8848/nacos/v2/ns/instance/list?serviceName=gateway-service&groupName=DEFAULT_GROUP&namespaceId=public'
```

### 9.5 查看 NAT 规则

```bash
sudo iptables -t nat -S | grep '111.229.17.125'
```

### 9.6 查看 gateway 容器到 auth 的公网回流

```bash
sudo docker exec gateway-service sh -lc 'curl -sS --max-time 5 -i http://111.229.17.125:8093/actuator/health | sed -n "1,20p"'
```

### 9.7 为什么 hairpin NAT 一配错就会把 SSH 打挂

最常见的原因不是 Docker，而是 iptables 匹配范围写大了：

- 如果规则只写了目标公网 IP，没有限制来源必须是 Docker 子网，那么所有发往 111.229.17.125 的入站连接都会进入 DNAT 流程
- 如果规则没有限制端口，而是把整个公网 IP 都改写到某个私网地址或容器地址，22 端口的 SSH 也会被一起改写
- 如果 POSTROUTING / MASQUERADE 规则覆盖过宽，回包路径会被破坏，已有 SSH 连接会直接失效

这就是为什么安全版本的脚本必须同时满足三点：

- 只匹配 Docker 子网源地址
- 只匹配明确列出的业务端口
- 只把流量回流到宿主机私网地址，而不是某个会变化的 172.* 容器地址


## 10. 一句话结论

本次故障的本质是：服务发现改成公网注册之后，同机容器回调公网地址的网络回流没有彻底治理干净，尤其是残留的旧 172.* DNAT 规则导致流量被错误转发。

最终正确修复方案是：

- Nginx 全面切到当前 QForge 路由
- 业务服务显式使用公网地址 111.229.17.125 注册 Nacos
- 统一使用宿主机私网回流的 hairpin NAT
- 删除所有旧的直连容器 DNAT 规则
- 启用除 auth 之外的核心微服务，优先恢复服务间调用

只要以后遵守这个部署模型，类似的 502、503、connection refused 和“注册正常但调用失败”的问题基本可以避免。