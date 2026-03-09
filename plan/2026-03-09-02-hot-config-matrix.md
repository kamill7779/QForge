# 热配置矩阵

## 已落地到配置文件的服务

- `backend/configs/question-core-service.yml`
- `backend/configs/exam-service.yml`
- `backend/configs/exam-parse-service.yml`
- 既有:
  - `backend/configs/auth-service.yml`
  - `backend/configs/gateway-service.yml`
  - `backend/configs/ocr-service.yml`
  - `backend/configs/persist-service.yml`

## 热配置与重启配置划分

| 服务 | 配置项 | 当前状态 | 说明 |
| --- | --- | --- | --- |
| `question-core-service` | `qforge.business.max-inline-images` 等业务阈值 | 热生效 | `ConfigurationProperties` 重绑后下次调用生效 |
| `question-core-service` | `qforge.business.ws-allowed-origins` | 热生效 | 新握手由运行时拦截器读取 |
| `question-core-service` | `qforge.cache.tag-catalog-ttl-seconds` | 热生效 | 新缓存写入使用最新 TTL |
| `question-core-service` | `qforge.cache.question-summary-ttl-seconds` | 热生效 | 新缓存写入使用最新 TTL |
| `exam-service` | `qforge.exam.default-duration-minutes` | 热生效 | 新建试卷/试题篮建卷走最新值 |
| `exam-service` | `qforge.exam.default-question-score` | 热生效 | 新建 section 默认分值走最新值 |
| `exam-service` | `qforge.cache.question-type-ttl-seconds` | 热生效 | 新缓存写入使用最新 TTL |
| `exam-service` | `qforge.cache.basket-ttl-seconds` | 热生效 | 新缓存写入使用最新 TTL |
| `exam-parse-service` | `qforge.business.max-exam-upload-files` | 热生效 | 上传校验走最新值 |
| `exam-parse-service` | `qforge.business.allowed-exam-extensions` | 热生效 | 上传扩展名校验走最新值 |
| `auth-service` | `security.jwt.secret` | 外化但需重启 | JWT signer 已改为 typed properties，但仍是启动期安全 Bean |
| `gateway-service` | `security.jwt.secret` | 外化但需重启 | JWT validator 仍是启动期安全链路 |
| `gateway-service` | `security.swagger-public` | 外化但需重启 | 安全链初始化后不做运行时重构 |
| `ocr-service` | API key / HTTP client timeout | 外化但需重启 | 底层 client 实例仍在启动时构造 |

## 本轮热配置改进点

- `question-core-service`
  - WebSocket Origin 从启动期固化改为运行时判定
- `exam-service`
  - 默认时长、默认分值从代码常量改为配置
- `auth-service` / `gateway-service`
  - 安全配置从分散 `@Value` 收敛到 typed properties

## 仍建议后续改进的点

1. `ocr-service` 若要真正支持热切换 API key / timeout，需要把底层 client 改为可刷新工厂。
2. `auth-service` / `gateway-service` 若要支持 JWT secret 热轮换，需要设计双 key 或 keyset 机制，不能只做简单热刷。
3. `persist-service` 目前业务配置很少，后续若新增写回策略再考虑细化热配置。
