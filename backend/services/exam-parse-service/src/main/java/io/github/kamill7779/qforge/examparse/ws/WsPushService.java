package io.github.kamill7779.qforge.examparse.ws;

import io.github.kamill7779.qforge.examparse.redis.RedisWsEventPublisher;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * WS 推送服务：通过 Redis Pub/Sub 将事件广播到持有 WS 连接的 question-core-service。
 */
@Service
public class WsPushService {

    private final RedisWsEventPublisher redisWsEventPublisher;

    public WsPushService(RedisWsEventPublisher redisWsEventPublisher) {
        this.redisWsEventPublisher = redisWsEventPublisher;
    }

    public void push(String user, String event, Map<String, Object> payload) {
        redisWsEventPublisher.publish(user, event, payload);
    }
}
