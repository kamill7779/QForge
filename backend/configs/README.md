# QForge Nacos 配置参考

本目录包含所有后端服务的 **Nacos 配置参考文件**。

## 使用方式

1. 打开 Nacos 控制台：http://localhost:8848/nacos
2. 进入 **配置管理 → 配置列表**
3. 为每个服务创建配置：
   - **Data ID**: `{service-name}.yml`（如 `auth-service.yml`）
   - **Group**: `DEFAULT_GROUP`
   - **配置格式**: YAML
   - **配置内容**: 参考本目录下对应的 YAML 文件
4. 配置发布后，服务会自动热加载（`@ConfigurationProperties` 标注的 Bean 会自动 rebind）

## 文件说明

| 文件 | Nacos Data ID | 描述 |
|------|--------------|------|
| `auth-service.yml` | `auth-service.yml` | JWT 密钥、过期时间、安全开关 |
| `gateway-service.yml` | `gateway-service.yml` | 网关内存限制、JWT、安全开关 |
| `question-service.yml` | `question-service.yml` | 业务参数 (图片/TTL/上传/WS) |
| `persist-service.yml` | `persist-service.yml` | MQ 重试参数 |
| `ocr-service.yml` | `ocr-service.yml` | OCR/LLM 模型配置、业务限制 |

## 配置热更新说明

- `@ConfigurationProperties` 标注的属性类支持 Nacos 推送热更新，**无需重启服务**
- `@Value` 注入的属性（如 JWT secret）需要重启服务才能生效
- `application.yml` 中的 Spring Boot 基础配置（如数据库连接）通常需要重启

## 环境变量优先级

`Nacos > 环境变量 > application.yml 默认值`
