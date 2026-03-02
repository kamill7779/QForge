package io.github.kamill7779.qforge.question.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopologyConfig {

    // --- OCR topology ---
    public static final String OCR_EXCHANGE = "qforge.ocr";
    public static final String OCR_RESULT_QUESTION_QUEUE = "qforge.ocr.result.question.q";
    public static final String ROUTING_TASK_RESULT = "ocr.task.result";

    // --- AI analysis topology ---
    public static final String AI_EXCHANGE = "qforge.ai";
    public static final String AI_ANALYSIS_TASK_QUEUE = "qforge.ai.analysis.task.q";
    public static final String AI_ANALYSIS_RESULT_QUEUE = "qforge.ai.analysis.result.question.q";
    public static final String ROUTING_AI_ANALYSIS_CREATED = "ai.analysis.created";
    public static final String ROUTING_AI_ANALYSIS_RESULT = "ai.analysis.result";

    @Bean
    public TopicExchange ocrExchange() {
        return new TopicExchange(OCR_EXCHANGE, true, false);
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
    public TopicExchange aiExchange() {
        return new TopicExchange(AI_EXCHANGE, true, false);
    }

    @Bean
    public Queue aiAnalysisTaskQueue() {
        return new Queue(AI_ANALYSIS_TASK_QUEUE, true);
    }

    @Bean
    public Binding aiAnalysisTaskBinding(
            @Qualifier("aiAnalysisTaskQueue") Queue aiAnalysisTaskQueue,
            @Qualifier("aiExchange") TopicExchange aiExchange
    ) {
        return BindingBuilder.bind(aiAnalysisTaskQueue).to(aiExchange).with(ROUTING_AI_ANALYSIS_CREATED);
    }

    @Bean
    public Queue aiAnalysisResultQueue() {
        return new Queue(AI_ANALYSIS_RESULT_QUEUE, true);
    }

    @Bean
    public Binding aiAnalysisResultBinding(
            @Qualifier("aiAnalysisResultQueue") Queue aiAnalysisResultQueue,
            @Qualifier("aiExchange") TopicExchange aiExchange
    ) {
        return BindingBuilder.bind(aiAnalysisResultQueue).to(aiExchange).with(ROUTING_AI_ANALYSIS_RESULT);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
