package io.github.kamill7779.qforge.persist.config;

import io.github.kamill7779.qforge.common.contract.DbPersistConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 声明 persist-service 所需的完整 RabbitMQ 拓扑（exchange + queue + binding）。
 */
@Configuration
public class RabbitTopologyConfig {

    @Bean
    public DirectExchange dbExchange() {
        return new DirectExchange(DbPersistConstants.DB_EXCHANGE, true, false);
    }

    @Bean
    public Queue dbPersistQueue() {
        return new Queue(DbPersistConstants.DB_PERSIST_QUEUE, true);
    }

    @Bean
    public Binding dbPersistBinding(
            @Qualifier("dbPersistQueue") Queue dbPersistQueue,
            @Qualifier("dbExchange") DirectExchange dbExchange
    ) {
        return BindingBuilder.bind(dbPersistQueue).to(dbExchange).with(DbPersistConstants.ROUTING_DB_PERSIST);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
