package io.github.kamill7779.qforge.examparse.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 通过 Redis Pub/Sub 发布 WS 推送事件。
 * <p>
 * exam-parse-service 不直接持有 WebSocket 连接，
 * 通过 Redis channel 广播后，question-core-service 中的
 * RedisWsEventListener 会转发到对应用户的 WS session。
 */
@Component
public class RedisWsEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisWsEventPublisher.class);

    /** Redis Pub/Sub 频道名 — 与 question-core-service 保持一致 */
    public static final String WS_PUSH_CHANNEL = "qforge:ws:push";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisWsEventPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void publish(String targetUser, String event, Map<String, Object> payload) {
        Map<String, Object> envelope = Map.of(
                "targetUser", targetUser,
                "event", event,
                "payload", payload
        );
        try {
            String json = objectMapper.writeValueAsString(envelope);
            redis.convertAndSend(WS_PUSH_CHANNEL, json);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize WS push event for user={}, event={}: {}",
                    targetUser, event, e.getMessage());
        }
    }
}
