package io.github.kamill7779.qforge.ocr.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;

@Configuration
public class RabbitTopologyConfig {

    public static final String OCR_EXCHANGE = "qforge.ocr";
    public static final String OCR_TASK_QUEUE = "qforge.ocr.task.q";
    public static final String OCR_RESULT_QUESTION_QUEUE = "qforge.ocr.result.question.q";
    public static final String ROUTING_TASK_CREATED = "ocr.task.created";
    public static final String ROUTING_TASK_RESULT = "ocr.task.result";

    @Bean
    public TopicExchange ocrExchange() {
        return new TopicExchange(OCR_EXCHANGE, true, false);
    }

    @Bean
    public Queue ocrTaskQueue() {
        return new Queue(OCR_TASK_QUEUE, true);
    }

    @Bean
    public Binding ocrTaskBinding(
            @Qualifier("ocrTaskQueue") Queue ocrTaskQueue,
            @Qualifier("ocrExchange") TopicExchange ocrExchange
    ) {
        return BindingBuilder.bind(ocrTaskQueue).to(ocrExchange).with(ROUTING_TASK_CREATED);
    }

    @Bean
    public Queue ocrResultQuestionQueue() {
        return new Queue(OCR_RESULT_QUESTION_QUEUE, true);
    }

    @Bean
    public Binding ocrResultQuestionBinding(
            @Qualifier("ocrResultQuestionQueue") Queue ocrResultQuestionQueue,
            @Qualifier("ocrExchange") TopicExchange ocrExchange
    ) {
        return BindingBuilder.bind(ocrResultQuestionQueue).to(ocrExchange).with(ROUTING_TASK_RESULT);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
