# QForge Nacos 配置参考

本目录包含所有后端服务的 Nacos 配置参考文件。

## 文件说明

| 文件 | Nacos Data ID | 描述 |
|------|--------------|------|
| `auth-service.yml` | `auth-service.yml` | JWT 密钥、过期时间、安全开关 |
| `gateway-service.yml` | `gateway-service.yml` | 网关内存限制、JWT、安全开关 |
| `question-core-service.yml` | `question-core-service.yml` | 题库核心服务业务参数与缓存 TTL |
| `question-basket-service.yml` | `question-basket-service.yml` | 试题篮与确认前组卷默认值 |
| `exam-service.yml` | `exam-service.yml` | 试卷默认值与缓存 TTL |
| `exam-parse-service.yml` | `exam-parse-service.yml` | 试卷解析上传约束 |
| `persist-service.yml` | `persist-service.yml` | MQ 重试参数 |
| `ocr-service.yml` | `ocr-service.yml` | OCR/LLM 模型配置、业务限制 |
