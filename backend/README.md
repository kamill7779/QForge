# QForge Backend

## Current Services

- `gateway-service`
- `auth-service`
- `question-core-service`
- `question-basket-service`
- `exam-service`
- `exam-parse-service`
- `ocr-service`
- `persist-service`
- `export-sidecar`

## Shared Libraries

- `libs/common-contract`
  - Async event contracts and shared infra constants.
- `libs/internal-api-contract`
  - Sync internal HTTP/Feign contracts.
- `libs/storage-support`
  - Shared COS storage abstraction for file-backed workflows.

## Core Infrastructure

- MySQL
- Redis
- RabbitMQ
- Nacos
- Tencent COS

Config references for Nacos are maintained under [`backend/configs`](./configs).

## Runtime Notes

- Java 17
- Spring Boot 3
- Spring Cloud Alibaba (Nacos discovery/config)
- Redis is used for task hot state, WS fan-out, tag catalog cache, question summary cache, and basket/question type cache.
- RabbitMQ is used for async OCR/AI/write-back flows.
- COS is used for large uploaded files in `exam-parse-service` and downstream OCR processing.

## Backend Split Rules

- Formal question data is owned by `question-core-service`.
- `question-basket-service` owns basket CRUD and pre-confirm compose state.
- `exam-parse-service` only manages temporary parse state until confirmation.
- `persist-service` is an async write-back service, not a shared repository layer.
- Sync service-to-service contracts should go into `internal-api-contract`.
- Async events/shared channel names should go into `common-contract`.

## Quick Start

From [`backend`](./):

```bash
docker compose -f docker-compose.dev.yml --profile local-infra up --build
```

Startup note:

- The local compose stack can start without `GLM_OCR_API_KEY` and `ZHIPUAI_API_KEY`.
- OCR / AI dependent routes require real upstream credentials to return successful results.
- `docker compose` waits for Nacos health before starting Nacos-dependent services.

Initialize schema before first startup:

```bash
mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uqforge -pqforge qforge < sql/init-schema.sql
```
