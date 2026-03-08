# QForge Nacos 配置迁移指南

## 概述

本文档列出了所有后端服务中**可配置/需配置的参数**，以及已从硬编码迁移为 Nacos 热配置的项目。

---

## 一、Nacos 配置快速上手

1. 访问 Nacos 控制台：http://localhost:8848/nacos（默认无认证）
2. 进入 **配置管理 → 配置列表**
3. 点击右上角 **+** 创建配置
4. 填写：
   - **Data ID**: 如 `question-service.yml`
   - **Group**: `DEFAULT_GROUP`
   - **配置格式**: YAML
   - **配置内容**: 复制 `backend/configs/` 下对应文件内容
5. 点击 **发布**

### 配置优先级

```
Nacos 热配置 > 环境变量 > application.yml 默认值
```

### 热更新 vs 重启

| 类型 | 热更新 | 说明 |
|------|:------:|------|
| `@ConfigurationProperties` | ✅ | Nacos 推送后自动 rebind |
| `@Value` | ❌ | 需要重启才生效 |
| Spring Boot 基础配置 | ❌ | 数据源/MQ 连接等需重启 |

---

## 二、各服务配置清单

### 2.1 auth-service（Nacos Data ID: `auth-service.yml`）

| 配置路径 | 类型 | 默认值 | 热更新 | 说明 |
|---------|------|--------|:------:|------|
| `security.jwt.secret` | String | `qforge-super-secret-...` | ❌ | JWT 签名密钥，**生产必改** |
| `security.jwt.expires-in-seconds` | long | `7200` | ❌ | JWT 过期时间（秒），默认 2 小时 |
| `security.swagger-public` | boolean | `false` | ❌ | 是否公开 Swagger UI |

**环境变量覆盖**：`JWT_SECRET`、`JWT_EXPIRES_IN_SECONDS`

---

### 2.2 gateway-service（Nacos Data ID: `gateway-service.yml`）

| 配置路径 | 类型 | 默认值 | 热更新 | 说明 |
|---------|------|--------|:------:|------|
| `security.jwt.secret` | String | 同 auth-service | ❌ | 必须与 auth-service 保持一致 |
| `security.swagger-public` | boolean | `false` | ❌ | 是否公开 Swagger UI |
| `spring.http.codec.max-in-memory-size` | String | `100MB` | ❌ | WebFlux 请求体内存上限 |

**环境变量覆盖**：`JWT_SECRET`、`GATEWAY_MAX_MEMORY_SIZE`

---

### 2.3 question-service（Nacos Data ID: `question-service.yml`）

#### 业务参数（`qforge.business.*` — 全部支持热更新 ✅）

| 配置路径 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `qforge.business.max-inline-images` | int | `10` | 每题最多内联图片数 |
| `qforge.business.max-image-binary-bytes` | int | `524288` | 每张图片字节上限（512 KB） |
| `qforge.business.max-reasoning-length` | int | `1024` | AI 推理文本截断长度 |
| `qforge.business.max-error-message-length` | int | `2048` | AI 错误信息截断长度 |
| `qforge.business.task-state-ttl-minutes` | int | `30` | AI/OCR 任务 Redis 热状态 TTL（分钟） |
| `qforge.business.answer-ocr-guard-ttl-minutes` | int | `10` | 答案 OCR 防重 TTL（分钟） |
| `qforge.business.answer-ocr-asset-ttl-hours` | int | `6` | 答案 OCR 资产缓存 TTL（小时） |
| `qforge.business.asset-cache-ttl-seconds` | int | `30` | OCR 结果图片缓存 TTL（秒） |
| `qforge.business.max-exam-upload-files` | int | `10` | 试卷上传最大文件数 |
| `qforge.business.allowed-exam-extensions` | String | `pdf,jpg,jpeg,png` | 允许的文件扩展名（逗号分隔） |
| `qforge.business.ws-allowed-origins` | String | `*` | WebSocket 允许的 Origin 模式 |

#### Spring 配置

| 配置路径 | 默认值 | 热更新 | 说明 |
|---------|--------|:------:|------|
| `spring.servlet.multipart.max-file-size` | `50MB` | ❌ | 单文件上传上限 |
| `spring.servlet.multipart.max-request-size` | `100MB` | ❌ | 请求总大小上限 |

---

### 2.4 persist-service（Nacos Data ID: `persist-service.yml`）

| 配置路径 | 类型 | 默认值 | 热更新 | 说明 |
|---------|------|--------|:------:|------|
| `spring.rabbitmq.listener.simple.retry.initial-interval` | Duration | `2000ms` | ❌ | MQ 首次重试间隔 |
| `spring.rabbitmq.listener.simple.retry.max-attempts` | int | `3` | ❌ | MQ 最大重试次数 |
| `spring.rabbitmq.listener.simple.retry.multiplier` | double | `2.0` | ❌ | MQ 重试间隔乘数 |
| `spring.rabbitmq.listener.simple.retry.max-interval` | Duration | `10000ms` | ❌ | MQ 最大重试间隔 |

---

### 2.5 ocr-service（Nacos Data ID: `ocr-service.yml`）

#### GLM OCR 布局解析（`ocr.provider.glm.*` — 支持热更新 ✅）

| 配置路径 | 默认值 | 环境变量 | 说明 |
|---------|--------|----------|------|
| `ocr.provider.glm.endpoint` | `https://api.z.ai/api/paas/v4/layout_parsing` | `GLM_OCR_ENDPOINT` | OCR API 地址 |
| `ocr.provider.glm.model` | `glm-ocr` | `GLM_OCR_MODEL` | OCR 模型名 |
| `ocr.provider.glm.api-key` | *(空)* | `GLM_OCR_API_KEY` | OCR API 密钥 |
| `ocr.provider.glm.image-mime-type` | `image/png` | `GLM_OCR_IMAGE_MIME_TYPE` | 图片 MIME 类型 |
| `ocr.provider.glm.timeout-seconds` | `60` | `GLM_OCR_TIMEOUT_SECONDS` | API 超时（秒） |
| `ocr.provider.glm.retry-max-attempts` | `3` | `GLM_OCR_RETRY_MAX_ATTEMPTS` | API 重试次数 |
| `ocr.provider.glm.retry-backoff-millis` | `800` | `GLM_OCR_RETRY_BACKOFF_MILLIS` | API 重试退避（毫秒） |
| `ocr.provider.glm.prefer-ipv4` | `true` | `GLM_OCR_PREFER_IPV4` | 是否优先 IPv4 |

#### ZhipuAI 推理模型（`zhipuai.*` — 支持热更新 ✅）

| 配置路径 | 默认值 | 环境变量 | 说明 |
|---------|--------|----------|------|
| `zhipuai.api-key` | *(空)* | `ZHIPUAI_API_KEY` | AI 分析 API 密钥 |
| `zhipuai.model` | `glm-5` | `ZHIPUAI_MODEL` | AI 分析模型名 |
| `zhipuai.temperature` | `0.1` | — | 生成温度 |
| `zhipuai.max-tokens` | `65536` | — | 最大 token 数 |

#### StemXml 转换模型（`stemxml.*` — 支持热更新 ✅）

| 配置路径 | 默认值 | 环境变量 | 说明 |
|---------|--------|----------|------|
| `stemxml.model` | `glm-4-0520` | `STEMXML_MODEL` | 题干 XML 转换模型 |
| `stemxml.temperature` | `0.1` | — | 生成温度 |
| `stemxml.max-tokens` | `65536` | — | 最大 token 数 |

#### AnswerXml 转换模型（`answerxml.*` — 支持热更新 ✅）

| 配置路径 | 默认值 | 环境变量 | 说明 |
|---------|--------|----------|------|
| `answerxml.model` | `glm-4-0520` | `ANSWERXML_MODEL` | 答案 XML 转换模型 |
| `answerxml.temperature` | `0.1` | — | 生成温度 |
| `answerxml.max-tokens` | `65536` | — | 最大 token 数 |

#### 试卷解析 LLM（`examparse.ai.*` — 支持热更新 ✅）

| 配置路径 | 默认值 | 环境变量 | 说明 |
|---------|--------|----------|------|
| `examparse.ai.model` | `glm-4-plus` | `EXAMPARSE_AI_MODEL` | 试卷拆题模型 |
| `examparse.ai.temperature` | `0.1` | — | 生成温度 |
| `examparse.ai.max-tokens` | `32768` | — | 最大 token 数 |

#### OCR 业务参数（`qforge.ocr.*` — 全部支持热更新 ✅）

| 配置路径 | 类型 | 默认值 | 说明 |
|---------|------|--------|------|
| `qforge.ocr.pdf-render-dpi` | int | `300` | PDF 渲染 DPI |
| `qforge.ocr.llm-empty-retries` | int | `2` | LLM 空内容重试次数 |
| `qforge.ocr.ai-default-max-tokens` | int | `65536` | AI 分析默认 max tokens |
| `qforge.ocr.ai-max-stem-chars` | int | `8000` | 题干文本截断长度 |
| `qforge.ocr.ai-max-single-answer-chars` | int | `2000` | 单答案截断长度 |
| `qforge.ocr.ai-max-answers` | int | `6` | 最多参考答案数 |

---

### 2.6 export-sidecar（Python，非 Nacos 配置）

| 环境变量 | 默认值 | 说明 |
|---------|--------|------|
| `NACOS_SERVER_ADDR` | `localhost:8848` | Nacos 连接地址 |
| `SERVICE_PORT` | `8092` | 服务端口 |
| `SERVICE_IP` | *(空)* | 注册 IP |
| `MAX_QUESTIONS_PER_EXPORT` | `200` | 单次导出最大题数 |
| `MAX_DOCX_SIZE_MB` | `50` | DOCX 文件大小上限（MB） |

---

## 三、本次配置化改造变更清单

### 从硬编码迁移到配置的项目

| 原始位置 | 原硬编码值 | 迁移到 | 说明 |
|---------|-----------|--------|------|
| `TaskStateRedisService.DEFAULT_TTL` | 30 分钟 | `qforge.business.task-state-ttl-minutes` | Redis 任务缓存 TTL |
| `TaskStateRedisService.ANSWER_OCR_GUARD_TTL` | 10 分钟 | `qforge.business.answer-ocr-guard-ttl-minutes` | 答案 OCR 防重 TTL |
| `TaskStateRedisService.ANSWER_OCR_ASSET_TTL` | 6 小时 | `qforge.business.answer-ocr-asset-ttl-hours` | 答案缓存 TTL |
| `OcrResultConsumer.ASSET_CACHE_TTL` | 30 秒 | `qforge.business.asset-cache-ttl-seconds` | 图片缓存 TTL |
| `ExamParseCommandService.MAX_FILES` | 10 | `qforge.business.max-exam-upload-files` | 上传文件数上限 |
| `ExamParseCommandService.ALLOWED_EXTENSIONS` | pdf,jpg,jpeg,png | `qforge.business.allowed-exam-extensions` | 允许的扩展名 |
| `WebSocketConfig` 硬编码 `"*"` | `*` | `qforge.business.ws-allowed-origins` | WS 跨域策略 |
| `PdfPageRenderer.RENDER_DPI` | 300 | `qforge.ocr.pdf-render-dpi` | PDF 渲染质量 |
| `StemXmlConverter.MAX_RETRIES` | 2 | `qforge.ocr.llm-empty-retries` | 空内容重试 |
| `AnswerXmlConverter.MAX_RETRIES` | 2 | `qforge.ocr.llm-empty-retries` | 空内容重试 |
| `ExamSplitLlmClient.MAX_RETRIES` | 2 | `qforge.ocr.llm-empty-retries` | 空内容重试 |
| `AiAnalysisTaskConsumer.DEFAULT_MAX_TOKENS` | 65536 | `qforge.ocr.ai-default-max-tokens` | AI 分析 token 上限 |
| `AiAnalysisTaskConsumer.MAX_STEM_CHARS` | 8000 | `qforge.ocr.ai-max-stem-chars` | 题干截断 |
| `AiAnalysisTaskConsumer.MAX_SINGLE_ANSWER_CHARS` | 2000 | `qforge.ocr.ai-max-single-answer-chars` | 答案截断 |
| `AiAnalysisTaskConsumer.MAX_ANSWERS` | 6 | `qforge.ocr.ai-max-answers` | 答案条数上限 |
| `auth-service` JWT 过期时间 | 7200 | `security.jwt.expires-in-seconds` + env | 环境变量覆盖 |
| `gateway-service` 内存上限 | 100MB | `spring.codec.max-in-memory-size` + env | 环境变量覆盖 |

---

## 四、基础设施公共配置（跨服务共享）

以下通过 **Docker Compose 环境变量** 传入，所有服务共享：

| 环境变量 | Docker Compose 值 | 说明 |
|---------|-------------------|------|
| `MYSQL_HOST` | `mysql` | MySQL 主机名 |
| `MYSQL_PORT` | `3306` | MySQL 端口 |
| `MYSQL_DB` | `qforge` | 数据库名 |
| `MYSQL_USER` | `qforge` | 数据库用户 |
| `MYSQL_PASSWORD` | `qforge` | 数据库密码 |
| `REDIS_HOST` | `redis` | Redis 主机名 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `RABBITMQ_HOST` | `rabbitmq` | RabbitMQ 主机名 |
| `RABBITMQ_PORT` | `5672` | RabbitMQ 端口 |
| `RABBITMQ_USER` | `guest` | RabbitMQ 用户 |
| `RABBITMQ_PASSWORD` | `guest` | RabbitMQ 密码 |
| `NACOS_SERVER_ADDR` | `nacos:8848` | Nacos 地址 |
| `JWT_SECRET` | `qforge-super-secret-...` | JWT 密钥 |

> 💡 这些基础设施连接参数通过环境变量传入即可，不建议放到 Nacos（需要连接 Nacos 本身前就得生效）。

---

## 五、安全注意事项

1. **API 密钥**（`GLM_OCR_API_KEY`、`ZHIPUAI_API_KEY`）通过宿主机 `.env` 文件注入，**不要写入 Nacos**
2. **JWT 密钥** 生产环境必须替换默认值
3. **WebSocket Origin** 生产环境应限制为实际域名
4. **MySQL root 密码** 在 `docker-compose.yml` 中为 `root`，生产环境必须替换
