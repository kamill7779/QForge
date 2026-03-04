package io.github.kamill7779.qforge.question.ws;

import io.github.kamill7779.qforge.question.redis.RedisWsEventPublisher;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * WS 推送服务：通过 Redis Pub/Sub 广播 WS 事件。
 * <p>
 * 多实例场景下，消费 MQ 的实例可能不持有目标用户的 WS session，
 * 因此改为发布到 Redis channel，所有实例的 {@code RedisWsEventListener}
 * 会接收并转发给本地 session。
 */
@Service
public class OcrWsPushService {

    private final RedisWsEventPublisher redisWsEventPublisher;

    public OcrWsPushService(RedisWsEventPublisher redisWsEventPublisher) {
        this.redisWsEventPublisher = redisWsEventPublisher;
    }

    public void push(String user, String event, Map<String, Object> payload) {
        redisWsEventPublisher.publish(user, event, payload);
    }
}
