package io.github.kamill7779.qforge.examparse.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exam parse MQ topology — 仅声明试卷解析相关的 exchange + queue。
 */
@Configuration
public class RabbitTopologyConfig {

    public static final String EXAM_EXCHANGE = "qforge.exam";
    public static final String EXAM_PARSE_TASK_QUEUE = "qforge.exam.parse.task.q";
    public static final String EXAM_PARSE_RESULT_QUEUE = "qforge.exam.parse.result.q";
    public static final String EXAM_PARSE_COMPLETED_QUEUE = "qforge.exam.parse.completed.q";
    public static final String ROUTING_EXAM_PARSE_TASK_CREATED = "exam.parse.task.created";
    public static final String ROUTING_EXAM_PARSE_RESULT = "exam.parse.result";
    public static final String ROUTING_EXAM_PARSE_COMPLETED = "exam.parse.completed";

    @Bean
    public TopicExchange examExchange() {
        return new TopicExchange(EXAM_EXCHANGE, true, false);
    }

    @Bean
    public Queue examParseTaskQueue() {
        return new Queue(EXAM_PARSE_TASK_QUEUE, true);
    }

    @Bean
    public Binding examParseTaskBinding(
            @Qualifier("examParseTaskQueue") Queue examParseTaskQueue,
            @Qualifier("examExchange") TopicExchange examExchange) {
        return BindingBuilder.bind(examParseTaskQueue).to(examExchange).with(ROUTING_EXAM_PARSE_TASK_CREATED);
    }

    @Bean
    public Queue examParseResultQueue() {
        return new Queue(EXAM_PARSE_RESULT_QUEUE, true);
    }

    @Bean
    public Binding examParseResultBinding(
            @Qualifier("examParseResultQueue") Queue examParseResultQueue,
            @Qualifier("examExchange") TopicExchange examExchange) {
        return BindingBuilder.bind(examParseResultQueue).to(examExchange).with(ROUTING_EXAM_PARSE_RESULT);
    }

    @Bean
    public Queue examParseCompletedQueue() {
        return new Queue(EXAM_PARSE_COMPLETED_QUEUE, true);
    }

    @Bean
    public Binding examParseCompletedBinding(
            @Qualifier("examParseCompletedQueue") Queue examParseCompletedQueue,
            @Qualifier("examExchange") TopicExchange examExchange) {
        return BindingBuilder.bind(examParseCompletedQueue).to(examExchange).with(ROUTING_EXAM_PARSE_COMPLETED);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
