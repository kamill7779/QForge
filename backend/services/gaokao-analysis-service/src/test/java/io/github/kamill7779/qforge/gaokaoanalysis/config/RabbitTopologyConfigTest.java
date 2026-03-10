package io.github.kamill7779.qforge.gaokaoanalysis.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.github.kamill7779.qforge.common.contract.GaokaoIndexingConstants;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class RabbitTopologyConfigTest {

    @Test
    void paperIndexRequestedQueueShouldDeclareDeadLetterRouting() {
        RabbitTopologyConfig config = new RabbitTopologyConfig();

        Queue queue = config.gaokaoPaperIndexRequestedQueue();

        assertEquals(
                GaokaoIndexingConstants.GAOKAO_INDEX_EXCHANGE,
                queue.getArguments().get("x-dead-letter-exchange")
        );
        assertEquals(
                GaokaoIndexingConstants.ROUTING_PAPER_INDEX_REQUESTED + ".dlq",
                queue.getArguments().get("x-dead-letter-routing-key")
        );
    }

    @Test
    void springContextShouldCreateBindingsWhenDlqQueueAlsoExists() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(RabbitTopologyConfig.class)) {
            assertEquals(2, context.getBeansOfType(Queue.class).size());
            assertNotNull(context.getBean("gaokaoPaperIndexRequestedBinding", Binding.class));
            assertNotNull(context.getBean("gaokaoPaperIndexRequestedDlqBinding", Binding.class));
        }
    }
}
