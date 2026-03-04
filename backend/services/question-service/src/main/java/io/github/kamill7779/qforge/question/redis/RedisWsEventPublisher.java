package io.github.kamill7779.qforge.question.redis;

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
 * 当多个 question-service 实例运行时，MQ 消费者可能在实例 A 上消费，
 * 而用户 WS 连接在实例 B。通过 Redis channel 广播，所有实例都能收到
 * 并将消息转发给自己本地持有的 WS session。
 */
@Component
public class RedisWsEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RedisWsEventPublisher.class);

    /** Redis Pub/Sub 频道名 */
    public static final String WS_PUSH_CHANNEL = "qforge:ws:push";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisWsEventPublisher(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布一条 WS 推送事件到 Redis channel。
     * 所有订阅了 {@value WS_PUSH_CHANNEL} 的实例都会收到。
     *
     * @param targetUser 目标用户名
     * @param event      WS 事件名（如 ocr.task.succeeded）
     * @param payload    事件负载
     */
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
            log.error("Failed to serialize WS push event for user={}, event={}: {}", targetUser, event, e.getMessage());
        }
    }
}
