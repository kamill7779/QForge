package io.github.kamill7779.qforge.question.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
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

    // --- DB async write-back topology ---
    /** Direct exchange 用于 question-service 内部落库写回任务。 */
    public static final String DB_EXCHANGE = "qforge.db";
    public static final String DB_PERSIST_QUEUE = "qforge.db.persist.q";
    public static final String ROUTING_DB_PERSIST = "db.persist";

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

    // --- DB write-back beans ---

    @Bean
    public DirectExchange dbExchange() {
        return new DirectExchange(DB_EXCHANGE, true, false);
    }

    @Bean
    public Queue dbPersistQueue() {
        return new Queue(DB_PERSIST_QUEUE, true);
    }

    @Bean
    public Binding dbPersistBinding(
            @Qualifier("dbPersistQueue") Queue dbPersistQueue,
            @Qualifier("dbExchange") DirectExchange dbExchange
    ) {
        return BindingBuilder.bind(dbPersistQueue).to(dbExchange).with(ROUTING_DB_PERSIST);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
