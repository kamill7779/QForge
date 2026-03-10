# Gaokao Analysis Spring AI/Qdrant Implementation

## Facts

- `gaokao-corpus-service` now uses `ocr-service /internal/ocr/recognize` to build `gk_ingest_ocr_page`, and draft bootstrap reads OCR text instead of filename placeholders.
- `gaokao-corpus-service` publish flow now writes published `gk_*` data in `INDEXING` state and emits `GaokaoPaperIndexRequestedEvent`.
- `gaokao-analysis-service` now contains Spring AI Zhipu chat/embedding wiring and a native Qdrant HTTP vector service.
- `gaokao-analysis-service` consumes gaokao publish indexing events, builds question/chunk vectors, computes recommendation edges, and callbacks corpus status/data.
- `question-core-service` now implements `/internal/questions/from-gaokao`.

## Implemented

- Shared contracts:
  - Added gaokao indexing MQ constants, publish event, and callback payload in `common-contract`.
- OCR:
  - Added sync OCR RPC `POST /internal/ocr/recognize` in `ocr-service`.
- Corpus:
  - Real OCR ingest persistence to `gk_ingest_ocr_page`.
  - Draft bootstrap based on OCR text and OCR page assets.
  - Draft question update now supports section relocation, options, answers, stem assets, and answer assets.
  - Publish copies question/answer assets and publishes async indexing event.
  - Internal callback persists `gk_rag_chunk`, `gk_vector_point`, `gk_recommend_edge`, and updates paper status.
  - Materialization now sends answers, tags, and assets.
- Analysis:
  - Added Spring AI Zhipu starter and config.
  - Replaced hardcoded single-question analysis with `ChatModel` + fallback structured JSON flow.
  - Added native Qdrant upsert/search implementation based on Spring `EmbeddingModel`.
  - Added gaokao paper indexing consumer.
- Question core:
  - Added gaokao materialization creation service and internal controller endpoint.

## Not Verified

- No Maven build/test run was executed because the current environment has no `mvn` and the repo has no `mvnw`.
- Qdrant HTTP payloads and Spring AI `ChatClient.create(...)` usage were implemented against the documented API shape, but not runtime-validated here.
- Draft child-question recursive create/delete flow is still not fully implemented; current update path supports section/option/answer/asset structure, and parent linkage on a single question.

## Risks

- Qdrant HTTP endpoints may require small shape adjustments depending on the deployed Qdrant version.
- Zhipu JSON output still depends on model compliance; fallback logic exists, but prompt tuning may still be needed for production quality.
- OCR sync RPC currently treats one source file as one OCR request; if PDF multi-page splitting is required at this service boundary, this path still needs page-level rendering support.
