# 高考录题 Web 前端实现说明

更新时间：2026-03-09

## 1. 目标

本次新增独立前端项目 gaokao-web，用于承接以下正式业务：

1. 整卷录入会话创建
2. 源文件上传
3. OCR + 分题触发
4. 草稿整卷与单题人工修订
5. 单题 / 整卷分析触发
6. 单题确认与整卷发布
7. 正式高考语料浏览
8. 物化到正式题库
9. 单题拍照检索

## 2. 项目位置

- 前端项目：gaokao-web
- Docker 接入：backend/docker-compose.yml

## 3. 设计原则

1. 高考录题前端独立于现有组卷 web 端，避免边界继续混杂。
2. 只经由 gateway 访问后端，不直连内部微服务。
3. 对当前未完成的高考后端接口保留真实路径接入，同时支持 VITE_GAOKAO_MOCK=true 的本地 mock 回退。
4. 页面结构按真实业务流组织，而不是沿用旧的 question-core 录题模型。

## 4. 已实现页面

1. 登录页
2. 整卷录入会话页
3. 草稿工作台页
4. 正式语料库页
5. 单题拍照检索页

## 5. 已实现能力

### 5.1 整卷录入

接入接口：

1. POST /api/gaokao/ingest-sessions
2. GET /api/gaokao/ingest-sessions
3. GET /api/gaokao/ingest-sessions/{sessionUuid}
4. POST /api/gaokao/ingest-sessions/{sessionUuid}/files
5. POST /api/gaokao/ingest-sessions/{sessionUuid}/ocr-split
6. GET /api/gaokao/ingest-sessions/{sessionUuid}/draft-paper

### 5.2 草稿编辑与发布

接入接口：

1. PUT /api/gaokao/draft-papers/{uuid}
2. PUT /api/gaokao/draft-questions/{uuid}
3. POST /api/gaokao/draft-questions/{uuid}/analyze
4. POST /api/gaokao/draft-papers/{uuid}/analyze
5. POST /api/gaokao/draft-questions/{uuid}/confirm
6. POST /api/gaokao/draft-papers/{uuid}/publish

### 5.3 正式语料与物化

接入接口：

1. GET /api/gaokao/corpus/papers
2. GET /api/gaokao/corpus/papers/{uuid}
3. GET /api/gaokao/corpus/questions/{uuid}
4. POST /api/gaokao/materialize

### 5.4 单题拍照检索

接入接口：

1. POST /api/gaokao/photo-query

## 6. Docker 部署

gaokao-web 使用 node 构建 + nginx 运行：

1. Dockerfile 在 gaokao-web/Dockerfile
2. nginx 反向代理 gateway-service:8080
3. backend/docker-compose.yml 已新增 gaokao-web 服务

默认宿主机端口：

1. 5175 -> gaokao-web:80

## 7. 当前后端缺口

以下接口路径已经在前端接入，但后端当前仓库实现仍未完成：

1. 草稿读取与草稿更新主服务实现
2. 分析触发后的结果读写闭环
3. 正式发布逻辑
4. 单题拍照检索正式实现
5. 文件上传控制器缺失

因此：

1. 联调真实后端时，已完成接口可直接走真实链路。
2. 未完成接口可通过构建参数 VITE_GAOKAO_MOCK=true 启动前端 mock 模式。

## 8. 后续建议

1. 先补齐 gaokao-corpus-service 的 draft / publish / photo-query 实现。
2. 再把 Draft DTO 扩到 section / question / option / answer / analysis preview 的完整结构。
3. 最后把当前工作台从“基础字段编辑器”升级到“整卷结构化编辑器 + 分析结果对比确认器”。