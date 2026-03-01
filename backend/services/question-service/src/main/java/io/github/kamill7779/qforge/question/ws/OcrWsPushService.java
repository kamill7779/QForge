package io.github.kamill7779.qforge.question.ws;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OcrWsPushService {

    private final QuestionWsHandler questionWsHandler;
    private final ObjectMapper objectMapper;

    public OcrWsPushService(QuestionWsHandler questionWsHandler, ObjectMapper objectMapper) {
        this.questionWsHandler = questionWsHandler;
        this.objectMapper = objectMapper;
    }

    public void push(String user, String event, Map<String, Object> payload) {
        Map<String, Object> message = Map.of(
                "event", event,
                "payload", payload
        );
        try {
            questionWsHandler.sendToUser(user, objectMapper.writeValueAsString(message));
        } catch (JsonProcessingException ignored) {
            // Ignore serialization failures for MVP push.
        }
    }
}

