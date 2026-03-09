package io.github.kamill7779.qforge.question.redis;

import io.github.kamill7779.qforge.common.contract.RedisChannelNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis Pub/Sub 配置：注册 WS 推送事件的监听容器。
 */
@Configuration
public class RedisPubSubConfig {

    @Bean
    public ChannelTopic wsPushTopic() {
        return new ChannelTopic(RedisChannelNames.WS_PUSH);
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisWsEventListener wsEventListener,
            ChannelTopic wsPushTopic
    ) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(wsEventListener, wsPushTopic);
        return container;
    }
}
