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

## `exam-service`

- Question basket.
- Question type catalog.
- Exam paper composition and export orchestration.

## `exam-parse-service`

- Exam upload and parse task lifecycle.
- Parsed-question preview/edit/confirm flow.
- Confirmation delegates formal creation to `question-core-service`.

## `ocr-service`

- OCR and split pipeline.
- AI-assisted parse generation.
- Publishes async result events.

## `persist-service`

- Async OCR/AI task history write-back.
- Should stay narrow in scope and not become a generic persistence facade.

## `gaokao-corpus-service`

- Gaokao math corpus business main service (port 8095).
- Ingest session management: upload, OCR split trigger, draft paper lifecycle.
- Draft paper/question editing and AI analysis preview/confirm.
- Formal corpus: published papers, questions, taxonomy, profiles.
- Similar question query entry point.
- Materialization bridge to `question-core-service` for formal exam composition.

## `gaokao-analysis-service`

- Gaokao math AI analysis service (port 8096).
- Text cleansing and XML normalization for math stems/answers.
- Knowledge tag, method tag, formula tag, difficulty analysis via LLM.
- Vector embedding and Qdrant index building.
- Similar question recall and reranking.
- RAG recommendation reason generation.
- Photo query orchestration (OCR → clean → analyze → search → rerank → RAG).
