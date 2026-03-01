# OCR Question Bank MVP Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Build a runnable MVP for OCR-driven question ingestion with asynchronous OCR, WebSocket push, draft-to-ready workflow, and REST-standardized APIs.

**Architecture:** Keep existing `gateway-service` and `auth-service`, add `question-service` and `ocr-service`, use OpenFeign for internal RPC submission, RabbitMQ for async OCR processing/results, Redis for idempotency/session hints, and MySQL + Flyway for durable state.

**Tech Stack:** Java 17, Spring Boot 3.5, Spring Cloud Alibaba (Nacos), Spring Cloud OpenFeign, RabbitMQ, Redis, MySQL 8.4, Flyway, JPA, WebSocket (Spring), JUnit 5 + Spring Boot Test.

---

**Execution skills to apply while implementing:** `@test-driven-development`, `@systematic-debugging`, `@verification-before-completion`, `@requesting-code-review`.

### Task 1: Register New Services in Build and Runtime Topology

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/docker-compose.yml`
- Modify: `backend/docker-compose.dev.yml`
- Modify: `backend/README.md`
- Test: `backend/pom.xml` module resolution by Maven reactor

**Step 1: Write the failing check**

Run:

```bash
cd backend
mvn -q -DskipTests -pl services/question-service -am validate
```

Expected: FAIL with `Could not find the selected project in the reactor`.

**Step 2: Add modules and compose service entries**

Add module declarations:

```xml
<module>services/question-service</module>
<module>services/ocr-service</module>
```

Add compose services `question-service` (8089) and `ocr-service` (8090), plus envs for Nacos/Rabbit/Redis/MySQL and GLM OCR config.

**Step 3: Re-run validation**

Run:

```bash
cd backend
mvn -q -DskipTests -pl services/question-service -am validate
```

Expected: PASS reactor module discovery (service code may still be incomplete later).

**Step 4: Commit**

```bash
git add backend/pom.xml backend/docker-compose.yml backend/docker-compose.dev.yml backend/README.md
git commit -m "chore(backend): register question and ocr services in reactor and compose"
```

### Task 2: Add OCR Event Contracts to Shared Library

**Files:**
- Create: `backend/libs/common-contract/src/main/java/io/github/kamill7779/qforge/common/contract/OcrTaskCreatedEvent.java`
- Create: `backend/libs/common-contract/src/main/java/io/github/kamill7779/qforge/common/contract/OcrTaskResultEvent.java`
- Create: `backend/libs/common-contract/src/test/java/io/github/kamill7779/qforge/common/contract/OcrEventSerializationTest.java`
- Test: `backend/libs/common-contract/src/test/java/io/github/kamill7779/qforge/common/contract/OcrEventSerializationTest.java`

**Step 1: Write the failing test**

```java
@Test
void shouldSerializeAndDeserializeOcrTaskResultEvent() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    OcrTaskResultEvent event = new OcrTaskResultEvent(
        "task-1", "QUESTION_STEM", "biz-1", "SUCCESS", "x^2+y^2=1", null, null
    );
    String json = mapper.writeValueAsString(event);
    OcrTaskResultEvent restored = mapper.readValue(json, OcrTaskResultEvent.class);
    assertEquals("SUCCESS", restored.status());
}
```

**Step 2: Run test to verify it fails**

Run:

```bash
cd backend
mvn -pl libs/common-contract -Dtest=OcrEventSerializationTest test
```

Expected: FAIL with class not found.

**Step 3: Implement minimal contracts**

```java
public record OcrTaskResultEvent(
    String taskUuid,
    String bizType,
    String bizId,
    String status,
    String recognizedText,
    String errorCode,
    String errorMessage
) {}
```

**Step 4: Re-run test**

Run:

```bash
cd backend
mvn -pl libs/common-contract -Dtest=OcrEventSerializationTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/libs/common-contract
git commit -m "feat(contract): add OCR task created/result events"
```

### Task 3: Scaffold `ocr-service` with Flyway Baseline and Task API

**Files:**
- Create: `backend/services/ocr-service/pom.xml`
- Create: `backend/services/ocr-service/src/main/java/io/github/kamill7779/qforge/ocr/OcrServiceApplication.java`
- Create: `backend/services/ocr-service/src/main/resources/application.yml`
- Create: `backend/services/ocr-service/src/main/resources/application-dev.yml`
- Create: `backend/services/ocr-service/src/main/resources/db/migration/V1__init_ocr.sql`
- Create: `backend/services/ocr-service/src/test/java/io/github/kamill7779/qforge/ocr/OcrTaskApiTest.java`
- Test: `backend/services/ocr-service/src/test/java/io/github/kamill7779/qforge/ocr/OcrTaskApiTest.java`

**Step 1: Write failing API test**

```java
@Test
void shouldCreateOcrTask() throws Exception {
    mockMvc.perform(post("/internal/ocr/tasks")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"bizType":"QUESTION_STEM","bizId":"q-1","imageUrl":"http://x/a.png","requestUserId":1}
            """))
        .andExpect(status().isAccepted())
        .andExpect(jsonPath("$.taskUuid").exists());
}
```

**Step 2: Run test and verify fail**

Run:

```bash
cd backend
mvn -pl services/ocr-service -Dtest=OcrTaskApiTest test
```

Expected: FAIL due to missing app/controller.

**Step 3: Implement minimal app + entity + repository + controller + migration**

SQL baseline excerpt:

```sql
CREATE TABLE q_ocr_task (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  task_uuid CHAR(36) NOT NULL UNIQUE,
  biz_type VARCHAR(32) NOT NULL,
  biz_id CHAR(36) NOT NULL,
  image_url VARCHAR(1024) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
```

**Step 4: Re-run tests**

Run:

```bash
cd backend
mvn -pl services/ocr-service -Dtest=OcrTaskApiTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/services/ocr-service
git commit -m "feat(ocr): bootstrap ocr-service with task API and Flyway baseline"
```

### Task 4: Implement OCR Worker, GLM Client, MQ Output, and Retry Semantics

**Files:**
- Create: `backend/services/ocr-service/src/main/java/io/github/kamill7779/qforge/ocr/client/GlmOcrClient.java`
- Create: `backend/services/ocr-service/src/main/java/io/github/kamill7779/qforge/ocr/mq/OcrTaskConsumer.java`
- Create: `backend/services/ocr-service/src/main/java/io/github/kamill7779/qforge/ocr/mq/OcrResultPublisher.java`
- Create: `backend/services/ocr-service/src/main/java/io/github/kamill7779/qforge/ocr/config/RabbitTopologyConfig.java`
- Create: `backend/services/ocr-service/src/test/java/io/github/kamill7779/qforge/ocr/OcrTaskConsumerTest.java`
- Test: `backend/services/ocr-service/src/test/java/io/github/kamill7779/qforge/ocr/OcrTaskConsumerTest.java`

**Step 1: Write failing consumer test**

```java
@Test
void shouldPublishSuccessEventWhenGlmReturnsText() {
    // given pending task + mocked GLM response
    // when consumer handles created event
    // then result publisher emits SUCCESS with recognizedText
}
```

**Step 2: Run test and verify fail**

Run:

```bash
cd backend
mvn -pl services/ocr-service -Dtest=OcrTaskConsumerTest test
```

Expected: FAIL with missing consumer/client beans.

**Step 3: Implement minimal worker path**

Consumer behavior:

```java
@RabbitListener(queues = "qforge.ocr.task.q")
public void onTaskCreated(OcrTaskCreatedEvent event) {
    // load task -> set PROCESSING -> call GLM -> set SUCCESS/FAILED -> publish OcrTaskResultEvent
}
```

**Step 4: Re-run test**

Run:

```bash
cd backend
mvn -pl services/ocr-service -Dtest=OcrTaskConsumerTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/services/ocr-service
git commit -m "feat(ocr): add MQ consumer, GLM client integration, and result publisher"
```

### Task 5: Scaffold `question-service` with Enterprise Schema Baseline

**Files:**
- Create: `backend/services/question-service/pom.xml`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/QuestionServiceApplication.java`
- Create: `backend/services/question-service/src/main/resources/application.yml`
- Create: `backend/services/question-service/src/main/resources/application-dev.yml`
- Create: `backend/services/question-service/src/main/resources/db/migration/V1__init_question_bank.sql`
- Create: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/FlywaySchemaSmokeTest.java`
- Test: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/FlywaySchemaSmokeTest.java`

**Step 1: Write failing schema smoke test**

```java
@Test
void shouldHaveQuestionAndAnswerTables() {
    Integer count = jdbcTemplate.queryForObject(
      "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name IN ('q_question','q_answer')",
      Integer.class
    );
    assertEquals(2, count);
}
```

**Step 2: Run test and verify fail**

Run:

```bash
cd backend
mvn -pl services/question-service -Dtest=FlywaySchemaSmokeTest test
```

Expected: FAIL due to missing migration or service module.

**Step 3: Implement service skeleton and V1 migration**

Include tables: `q_question`, `q_answer`, `q_tag`, `q_question_tag_rel`, `q_user_question_rel`, `q_ocr_confirm_snapshot`, `q_question_asset`, `q_paper`, `q_paper_question_rel`.

**Step 4: Re-run test**

Run:

```bash
cd backend
mvn -pl services/question-service -Dtest=FlywaySchemaSmokeTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/services/question-service
git commit -m "feat(question): bootstrap question-service and enterprise schema baseline"
```

### Task 6: Implement REST-Standard Draft/Answer/Complete APIs

**Files:**
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/controller/QuestionController.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/controller/AnswerController.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/service/QuestionCommandService.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/config/GlobalExceptionHandler.java`
- Create: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/QuestionApiRestStandardTest.java`
- Test: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/QuestionApiRestStandardTest.java`

**Step 1: Write failing REST contract tests**

Test targets:
- `POST /api/questions` returns `201`.
- `POST /api/questions/{uuid}/answers` returns `201`.
- `POST /api/questions/{uuid}/complete` returns `422` if stem or answers missing.
- Error body includes `code/message/traceId/details`.

**Step 2: Run test and verify fail**

Run:

```bash
cd backend
mvn -pl services/question-service -Dtest=QuestionApiRestStandardTest test
```

Expected: FAIL with 404 or assertion mismatch.

**Step 3: Implement minimal endpoints and validation**

Sample completion rule check:

```java
if (question.getStemText() == null || answerCount == 0) {
    throw new BusinessValidationException("QUESTION_COMPLETE_VALIDATION_FAILED", ...);
}
```

**Step 4: Re-run tests**

Run:

```bash
cd backend
mvn -pl services/question-service -Dtest=QuestionApiRestStandardTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/services/question-service
git commit -m "feat(question): add draft answer complete APIs with REST-standard errors"
```

### Task 7: Integrate Feign RPC to `ocr-service` and Consume OCR Result Events

**Files:**
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/client/OcrServiceClient.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/controller/OcrTaskController.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/mq/OcrResultConsumer.java`
- Modify: `backend/services/question-service/src/main/resources/application.yml`
- Create: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/OcrIntegrationFlowTest.java`
- Test: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/OcrIntegrationFlowTest.java`

**Step 1: Write failing flow test**

Test targets:
- `POST /api/questions/{uuid}/ocr-tasks` returns `202` and `taskUuid`.
- Simulate `OcrTaskResultEvent` consumption updates task status.

**Step 2: Run test and verify fail**

Run:

```bash
cd backend
mvn -pl services/question-service -Dtest=OcrIntegrationFlowTest test
```

Expected: FAIL due to missing Feign client / listener.

**Step 3: Implement Feign + listener**

Feign excerpt:

```java
@FeignClient(name = "ocr-service", path = "/internal/ocr/tasks")
public interface OcrServiceClient {
    @PostMapping
    OcrTaskAcceptedResponse createTask(@RequestBody OcrTaskCreateRequest req);
}
```

**Step 4: Re-run tests**

Run:

```bash
cd backend
mvn -pl services/question-service -Dtest=OcrIntegrationFlowTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/services/question-service
git commit -m "feat(question): integrate ocr-service via feign and consume OCR result events"
```

### Task 8: Add WebSocket Push Channel for OCR Status and Result

**Files:**
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/ws/WebSocketConfig.java`
- Create: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/ws/OcrWsPushService.java`
- Create: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/OcrWsPushServiceTest.java`
- Modify: `backend/services/question-service/src/main/java/io/github/kamill7779/qforge/question/mq/OcrResultConsumer.java`
- Test: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/OcrWsPushServiceTest.java`

**Step 1: Write failing push test**

```java
@Test
void shouldPushSucceededEventToOwnerChannel() {
    // given owner user session connected
    // when OCR result consumed
    // then ws message event=ocr.task.succeeded delivered once
}
```

**Step 2: Run test and verify fail**

Run:

```bash
cd backend
mvn -pl services/question-service -Dtest=OcrWsPushServiceTest test
```

Expected: FAIL due to missing WS implementation.

**Step 3: Implement WS endpoint and push service**

Event payload should include:

```json
{"event":"ocr.task.succeeded","taskUuid":"...","bizType":"QUESTION_STEM","bizId":"...","recognizedText":"..."}
```

**Step 4: Re-run tests**

Run:

```bash
cd backend
mvn -pl services/question-service -Dtest=OcrWsPushServiceTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/services/question-service
git commit -m "feat(question): add websocket push for OCR async status"
```

### Task 9: Route New APIs Through Gateway and Add Auth Rules

**Files:**
- Modify: `backend/services/gateway-service/src/main/resources/application.yml`
- Modify: `backend/services/gateway-service/src/main/java/io/github/kamill7779/qforge/gateway/filter/JwtAuthGlobalFilter.java`
- Create: `backend/services/gateway-service/src/test/java/io/github/kamill7779/qforge/gateway/GatewayRouteTest.java`
- Test: `backend/services/gateway-service/src/test/java/io/github/kamill7779/qforge/gateway/GatewayRouteTest.java`

**Step 1: Write failing route test**

Test targets:
- `/api/questions/**` routes to `question-service`.
- `/api/questions/**` requires JWT.
- Internal `/internal/ocr/**` is not publicly exposed through gateway.

**Step 2: Run test and verify fail**

Run:

```bash
cd backend
mvn -pl services/gateway-service -Dtest=GatewayRouteTest test
```

Expected: FAIL for missing route/policy.

**Step 3: Implement route config**

YAML excerpt:

```yaml
- id: question-service-route
  uri: lb://question-service
  predicates:
    - Path=/api/questions/**,/api/answers/**,/api/ocr-tasks/**
```

**Step 4: Re-run tests**

Run:

```bash
cd backend
mvn -pl services/gateway-service -Dtest=GatewayRouteTest test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/services/gateway-service
git commit -m "feat(gateway): route question APIs and enforce auth boundaries"
```

### Task 10: End-to-End Verification and Release Notes

**Files:**
- Modify: `backend/README.md`
- Create: `backend/services/question-service/src/test/java/io/github/kamill7779/qforge/question/QuestionMvpE2ESmokeTest.java`
- Create: `backend/services/ocr-service/src/test/java/io/github/kamill7779/qforge/ocr/OcrMvpE2ESmokeTest.java`
- Test: `backend` reactor modules

**Step 1: Write failing smoke tests**

Smoke assertions:
- Create draft -> create OCR task -> consume success event -> confirm text -> complete question -> status READY.

**Step 2: Run full relevant test suite**

Run:

```bash
cd backend
mvn -pl libs/common-contract,services/ocr-service,services/question-service,services/gateway-service -am test
```

Expected: FAIL initially.

**Step 3: Fill missing wiring and docs**

Update README with:
- startup commands
- GLM OCR env variables
- REST endpoint summary and example status codes
- WS event names

**Step 4: Re-run verification**

Run:

```bash
cd backend
mvn -pl libs/common-contract,services/ocr-service,services/question-service,services/gateway-service -am test
```

Expected: PASS.

**Step 5: Commit**

```bash
git add backend/README.md backend/services/question-service backend/services/ocr-service
git commit -m "test/docs: verify OCR question-bank MVP flow and document usage"
```

---

## REST Compliance Checklist (Must Pass Before Merge)

- Resource-first URIs (`/questions`, `/answers`, `/ocr-tasks`), no verb-style endpoints except explicit action `/complete`.
- Correct status codes: `201/202/422/409/...` as designed.
- Uniform error payload fields: `code`, `message`, `traceId`, `details`.
- AuthN/AuthZ behavior validated by tests.
- Idempotency behavior verified for OCR task creation.

---

## Verification Commands (Final Gate)

```bash
cd backend
mvn -pl libs/common-contract,services/ocr-service,services/question-service,services/gateway-service -am test
docker compose up --build -d
```

Expected:
- All selected module tests PASS.
- Services healthy: gateway/auth/question/ocr + mysql/redis/rabbitmq/nacos.

