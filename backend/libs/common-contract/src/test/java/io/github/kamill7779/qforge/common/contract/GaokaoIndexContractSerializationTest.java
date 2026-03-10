package io.github.kamill7779.qforge.common.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class GaokaoIndexContractSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void gaokaoIndexRequestedEventShouldRoundTrip() throws Exception {
        GaokaoPaperIndexRequestedEvent event = new GaokaoPaperIndexRequestedEvent(
                "evt-1",
                11L,
                "paper-uuid",
                "2025 全国卷",
                "2025",
                "NATIONWIDE",
                "2026-03-10T09:00:00",
                List.of(new GaokaoPaperIndexRequestedEvent.QuestionPayload(
                        101L,
                        "question-uuid",
                        "1",
                        "CHOICE_SINGLE",
                        "OBJECTIVE",
                        "设函数 f(x)=x^2",
                        "<stem><p>设函数 f(x)=x^2</p></stem>",
                        "设函数f(x)=x^2",
                        new BigDecimal("0.55"),
                        "MEDIUM",
                        "[\"FUNCTION\"]",
                        "[\"TRANSFORM\"]",
                        "[]",
                        "[]",
                        "[\"LOGIC\"]",
                        "[\"识别条件\",\"计算\"]",
                        "函数基础题",
                        List.of(new GaokaoPaperIndexRequestedEvent.AnswerPayload(
                                1001L,
                                "x=1",
                                "<answer><p>x=1</p></answer>",
                                true,
                                1
                        ))
                ))
        );

        String json = objectMapper.writeValueAsString(event);
        GaokaoPaperIndexRequestedEvent restored =
                objectMapper.readValue(json, GaokaoPaperIndexRequestedEvent.class);

        assertEquals("paper-uuid", restored.paperUuid());
        assertEquals(1, restored.questions().size());
        assertEquals("question-uuid", restored.questions().get(0).questionUuid());
    }

    @Test
    void gaokaoIndexCallbackShouldRoundTrip() throws Exception {
        GaokaoIndexCallbackRequest request = new GaokaoIndexCallbackRequest(
                11L,
                "paper-uuid",
                "READY",
                null,
                List.of(new GaokaoIndexCallbackRequest.RagChunkPayload(
                        101L,
                        "chunk-1",
                        "STEM",
                        "函数题干",
                        12
                )),
                List.of(new GaokaoIndexCallbackRequest.VectorPointPayload(
                        "QUESTION",
                        101L,
                        "joint",
                        "gk_question_vectors",
                        "question-101-joint",
                        "{\"questionId\":101}",
                        "ACTIVE"
                )),
                List.of(new GaokaoIndexCallbackRequest.RecommendEdgePayload(
                        101L,
                        102L,
                        "SAME_CLASS",
                        new BigDecimal("0.88")
                ))
        );

        String json = objectMapper.writeValueAsString(request);
        GaokaoIndexCallbackRequest restored =
                objectMapper.readValue(json, GaokaoIndexCallbackRequest.class);

        assertEquals("READY", restored.status());
        assertEquals(1, restored.vectorPoints().size());
        assertEquals("joint", restored.vectorPoints().get(0).vectorKind());
    }
}
