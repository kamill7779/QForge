package io.github.kamill7779.qforge.question.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    public static final String OCR_EXCHANGE = "qforge.ocr";
    public static final String OCR_RESULT_QUESTION_QUEUE = "qforge.ocr.result.question.q";
    public static final String ROUTING_TASK_RESULT = "ocr.task.result";

    @Bean
    public TopicExchange ocrExchange() {
        return new TopicExchange(OCR_EXCHANGE, true, false);
    }

    @Bean
    public Queue ocrResultQuestionQueue() {
        return new Queue(OCR_RESULT_QUESTION_QUEUE, true);
    }

    @Bean
    public Binding ocrResultQuestionBinding(Queue ocrResultQuestionQueue, TopicExchange ocrExchange) {
        return BindingBuilder.bind(ocrResultQuestionQueue).to(ocrExchange).with(ROUTING_TASK_RESULT);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
