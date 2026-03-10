package io.github.kamill7779.qforge.gaokaoanalysis.config;

import io.github.kamill7779.qforge.common.contract.GaokaoIndexingConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    @Bean
    public TopicExchange gaokaoIndexExchange() {
        return new TopicExchange(GaokaoIndexingConstants.GAOKAO_INDEX_EXCHANGE, true, false);
    }

    @Bean
    public Queue gaokaoPaperIndexRequestedQueue() {
        return new Queue(
                GaokaoIndexingConstants.PAPER_INDEX_REQUESTED_QUEUE,
                true,
                false,
                false,
                java.util.Map.of(
                        "x-dead-letter-exchange", GaokaoIndexingConstants.GAOKAO_INDEX_EXCHANGE,
                        "x-dead-letter-routing-key", GaokaoIndexingConstants.ROUTING_PAPER_INDEX_REQUESTED_DLQ
                )
        );
    }

    @Bean
    public Queue gaokaoPaperIndexRequestedDlq() {
        return new Queue(GaokaoIndexingConstants.PAPER_INDEX_REQUESTED_DLQ, true);
    }

    @Bean
    public Binding gaokaoPaperIndexRequestedBinding(
            @Qualifier("gaokaoPaperIndexRequestedQueue") Queue gaokaoPaperIndexRequestedQueue,
            @Qualifier("gaokaoIndexExchange") TopicExchange gaokaoIndexExchange) {
        return BindingBuilder.bind(gaokaoPaperIndexRequestedQueue)
                .to(gaokaoIndexExchange)
                .with(GaokaoIndexingConstants.ROUTING_PAPER_INDEX_REQUESTED);
    }

    @Bean
    public Binding gaokaoPaperIndexRequestedDlqBinding(
            @Qualifier("gaokaoPaperIndexRequestedDlq") Queue gaokaoPaperIndexRequestedDlq,
            @Qualifier("gaokaoIndexExchange") TopicExchange gaokaoIndexExchange) {
        return BindingBuilder.bind(gaokaoPaperIndexRequestedDlq)
                .to(gaokaoIndexExchange)
                .with(GaokaoIndexingConstants.ROUTING_PAPER_INDEX_REQUESTED_DLQ);
    }
}
