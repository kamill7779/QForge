package io.github.kamill7779.qforge.common.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class OcrEventSerializationTest {

    @Test
    void shouldSerializeAndDeserializeOcrTaskResultEvent() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        OcrTaskResultEvent event = new OcrTaskResultEvent(
                "task-1",
                "QUESTION_STEM",
                "biz-1",
                "SUCCESS",
                "x^2+y^2=1",
                null,
                null,
                "admin",
                "2026-02-28T12:00:00Z"
        );

        String json = mapper.writeValueAsString(event);
        OcrTaskResultEvent restored = mapper.readValue(json, OcrTaskResultEvent.class);

        assertEquals("SUCCESS", restored.status());
        assertEquals("x^2+y^2=1", restored.recognizedText());
    }
}

