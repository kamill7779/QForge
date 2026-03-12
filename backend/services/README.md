# Services Overview

## `gateway-service`

- Frontend entry.
- JWT validation.
- Route forwarding through Nacos discovery.

## `auth-service`

- Username/password login.
- JWT issuing and token verification endpoints.

## `question-core-service`

- Question canonical data.
- Tag catalog and tag relations.
- OCR/AI task hot state and WS relay.
- Internal summary/full-data APIs for other backend services.

## `question-basket-service`

- Question basket item management.
- Pre-confirm compose state.
- Confirm compose into real exam papers through `exam-service`.

## `exam-service`

- Question type catalog.
- Persisted exam paper composition and export orchestration.

## `exam-parse-service`

- Exam upload and parse task lifecycle.
- Parsed-question preview/edit/confirm flow.
- Confirmation delegates formal creation to `question-core-service`.
- Source files are stored in COS instead of large MySQL blobs.

## `ocr-service`

- OCR and split pipeline for parse tasks.
- AI-assisted parse generation.
- Publishes async result events.

## `persist-service`

- Async OCR/AI task history write-back.
- Should stay narrow in scope and not become a generic persistence facade.

## `export-sidecar`

- Internal Python docx rendering service.
- Only accepts internal render payloads from `exam-service`.
