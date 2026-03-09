# QForge Microservice Architecture

## Service Topology

- `gateway-service`
  - Unified frontend entry.
  - JWT validation and route forwarding.
  - Security config is externalized, but JWT secret remains restart-scoped.
- `auth-service`
  - Username/password login and JWT issuing.
  - Owns account authentication state and token signing rules.
- `question-core-service`
  - Owns question bank core domain: question, answer, tag, OCR/AI task state, WS relay.
  - Exposes internal summary/full-data APIs for other backend services.
  - Accepts parsed-question confirmation from `exam-parse-service`.
- `exam-service`
  - Owns question basket, question types, exam paper composition, and export orchestration.
  - Depends on `question-core-service` for question summaries and full export payloads.
- `exam-parse-service`
  - Owns exam upload, parse task tracking, preview/edit/confirm workflow.
  - Does not write formal questions directly; confirmation now goes through `question-core-service`.
- `ocr-service`
  - OCR, split, and AI-assisted parse pipeline.
  - Produces async events consumed by domain services.
- `persist-service`
  - Async write-back sink for OCR/AI task history.
  - Not a generic persistence abstraction layer.
- `export-sidecar`
  - Word export rendering service used by `exam-service`.

## Shared Libraries

- `libs/common-contract`
  - Async MQ event models and shared infrastructure constants.
  - Should contain cross-service event/queue/channel names only.
- `libs/internal-api-contract`
  - Sync internal HTTP/Feign contracts between microservices.
  - Current critical use: `question-core-service` internal APIs.

## Integration Patterns

- Sync:
  - Frontend -> `gateway-service` -> domain services
  - `exam-service` / `exam-parse-service` -> `question-core-service`
- Async:
  - `ocr-service` -> RabbitMQ -> domain consumers
  - Domain services -> RabbitMQ -> `persist-service`
  - Redis Pub/Sub for cross-instance WS fan-out
- Service discovery and config:
  - Nacos discovery
  - Nacos config with service-name-aligned data IDs

## Current Ownership Boundaries

- `auth-service`: auth credentials and token issuance.
- `question-core-service`: question canonical data and tag truth.
- `exam-service`: exam composition state and basket state.
- `exam-parse-service`: parse-session temporary state before confirmation.
- `persist-service`: async task result history only.

All services still run against the shared `qforge` MySQL schema, but logical ownership is now separated by service responsibility rather than by monolithic module boundaries.
