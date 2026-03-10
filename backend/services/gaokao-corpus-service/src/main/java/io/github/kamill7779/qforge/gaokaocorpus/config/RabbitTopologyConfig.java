package io.github.kamill7779.qforge.gaokaocorpus.config;

import io.github.kamill7779.qforge.common.contract.GaokaoIndexingConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange gaokaoIndexExchange() {
        return new TopicExchange(GaokaoIndexingConstants.GAOKAO_INDEX_EXCHANGE, true, false);
    }
}
