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

说明：

1. /files 当前已改为真实后端链路，gaokao-corpus-service 会记录 gk_ingest_source_file 元数据并把源文件落到本地上传目录。
2. OCR 触发后，gaokao-corpus-service 现已按上传源文件自动生成可编辑的草稿模板，前端可以直接进入工作台做整卷和单题修订。
3. 当前草稿模板仍属于“后端模板闭环”，不是正式 OCR 分题结果：默认按源文件生成占位 section 和题目，供人工修订与后续分析链路继续推进。

### 5.2 草稿编辑与发布

接入接口：

1. PUT /api/gaokao/draft-papers/{uuid}
2. PUT /api/gaokao/draft-questions/{uuid}
3. POST /api/gaokao/draft-questions/{uuid}/analyze
4. POST /api/gaokao/draft-papers/{uuid}/analyze
5. POST /api/gaokao/draft-questions/{uuid}/confirm
6. POST /api/gaokao/draft-papers/{uuid}/publish

说明：

1. Draft DTO 现已带出选项、答案和最新 analysis preview，后续前端可直接升级成结构化编辑器与分析对比确认器。
2. 发布链路会把已确认草稿复制到 gk_paper / gk_question / gk_question_profile 等正式表。

### 5.3 正式语料与物化

接入接口：

1. GET /api/gaokao/corpus/papers
2. GET /api/gaokao/corpus/papers/{uuid}
3. GET /api/gaokao/corpus/questions/{uuid}
4. POST /api/gaokao/materialize

说明：

1. 物化接口在真实链路下优先按 questionUuid 调用，不要求前端暴露数据库主键。

### 5.4 单题拍照检索

接入接口：

1. POST /api/gaokao/photo-query

说明：

1. 对外响应现已补齐 queryQuestion、ocrRaw、analysisProfile、recommendGroups、reasonSummary，并兼容保留 results 扁平列表给现有前端页面使用。

## 6. Docker 部署

gaokao-web 使用 node 构建 + nginx 运行：

1. Dockerfile 在 gaokao-web/Dockerfile
2. nginx 反向代理 gateway-service:8080
3. backend/docker-compose.yml 已新增 gaokao-web 服务

默认宿主机端口：

1. 5175 -> gaokao-web:80

相关重建启动入口：

1. backend/scripts/docker-rebuild-gaokao.sh
2. backend 目录下也可直接执行 docker compose up -d --build gateway-service gaokao-corpus-service gaokao-analysis-service gaokao-web

## 7. 当前后端缺口

以下接口路径已经在前端接入，但后端当前仓库实现仍未完成：

1. 真实 OCR 分题结果尚未替换当前按源文件生成的草稿模板
2. Draft / Corpus 详情虽然已带出 option / answer / analysis preview 结构，但前端当前仍只消费基础字段
3. 单题拍照检索已打通编排链路，但推荐质量仍依赖 gaokao-analysis-service 的 OCR、向量检索与 RAG 能力质量
4. 发布后写入 Qdrant / RAG chunk 的正式构建流程仍应继续在 gaokao-analysis-service 完成

因此：

1. 联调真实后端时，已完成接口可直接走真实链路。
2. 当前 mock 模式仍可保留给离线 UI 开发，但不再是草稿工作台联调的必需条件。

## 8. 后续建议

1. 先把真实 OCR 分题结果替换当前源文件占位题模板。
2. 再让工作台消费 Draft DTO 中已有的 option / answer / analysis preview 结构。
3. 最后补齐发布后的向量入库、推荐边和 RAG chunk 正式构建闭环。