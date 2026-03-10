# QForge Backend

## Current Services

- `gateway-service`
- `auth-service`
- `question-core-service`
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

## Core Infrastructure

- MySQL
- Redis
- RabbitMQ
- Nacos

Config references for Nacos are maintained under [`backend/configs`](./configs).

## Runtime Notes

- Java 17
- Spring Boot 3
- Spring Cloud Alibaba (Nacos discovery/config)
- Redis is used for:
  - task hot state
  - WS fan-out
  - tag catalog cache
  - question summary cache
  - exam basket/question type cache
- RabbitMQ is used for async OCR/AI/write-back flows

## Backend Split Rules

- Formal question data is owned by `question-core-service`.
- `exam-parse-service` only manages temporary parse state until confirmation.
- `persist-service` is an async write-back service, not a shared repository layer.
- Sync service-to-service contracts should go into `internal-api-contract`.
- Async events/shared channel names should go into `common-contract`.

## Quick Start

From [`backend`](./):

```bash
docker compose up --build
```

For the gaokao template flow only, rebuild and start the relevant stack with:

```bash
./scripts/docker-rebuild-gaokao.sh
```

Startup note:

- The full Docker Compose stack can start without `GLM_OCR_API_KEY` and `ZHIPUAI_API_KEY`.
- OCR / AI dependent routes will require real upstream credentials to return successful results.
- `docker compose` now waits for Nacos health before starting Nacos-dependent services, instead of only waiting for the Nacos container process to exist.

Initialize schema before first startup:

```bash
mysql --default-character-set=utf8mb4 -h127.0.0.1 -P3306 -uqforge -pqforge qforge < sql/init-schema.sql
```

## Gateway/Auth Entry

- Login: `POST http://localhost:8080/api/auth/login`
- Auth profile check: `GET http://localhost:8080/api/auth/me`
- Gateway ping: `GET http://localhost:8080/gateway/ping`

## Swagger

- Gateway: `http://localhost:8080/swagger-ui.html`
- Auth: `http://localhost:8088/swagger-ui.html`

Other services expose their own Swagger endpoints on their service ports.
