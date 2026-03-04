package io.github.kamill7779.qforge.question.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

/**
 * Redis Pub/Sub 订阅端：接收 WS 推送事件并转发到本实例的 WebSocket session。
 */
@Component
public class RedisWsEventListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RedisWsEventListener.class);

    private final io.github.kamill7779.qforge.question.ws.QuestionWsHandler wsHandler;
    private final ObjectMapper objectMapper;

    public RedisWsEventListener(
            io.github.kamill7779.qforge.question.ws.QuestionWsHandler wsHandler,
            ObjectMapper objectMapper
    ) {
        this.wsHandler = wsHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            Map<String, Object> envelope = objectMapper.readValue(json, Map.class);
            String targetUser = (String) envelope.get("targetUser");
            String event = (String) envelope.get("event");
            Object payload = envelope.get("payload");

            // 重新构建前端期望的 WS 消息格式: {event, payload}
            Map<String, Object> wsMessage = Map.of("event", event, "payload", payload);
            String wsJson = objectMapper.writeValueAsString(wsMessage);

            wsHandler.sendToUser(targetUser, wsJson);
        } catch (Exception e) {
            log.warn("Failed to process Redis WS push message: {}", e.getMessage());
        }
    }
}
