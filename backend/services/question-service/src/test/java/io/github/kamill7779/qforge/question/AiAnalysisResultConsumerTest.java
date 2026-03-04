package io.github.kamill7779.qforge.question;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.AiAnalysisResultEvent;
import io.github.kamill7779.qforge.common.contract.DbPersistConstants;
import io.github.kamill7779.qforge.common.contract.DbWriteBackEvent;
import io.github.kamill7779.qforge.question.config.QForgeBusinessProperties;
import io.github.kamill7779.qforge.question.mq.AiAnalysisResultConsumer;
import io.github.kamill7779.qforge.question.redis.TaskStateRedisService;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class AiAnalysisResultConsumerTest {

    @Mock
    private OcrWsPushService wsPushService;

    @Mock
    private TaskStateRedisService taskStateRedisService;

    @Mock
    private RabbitTemplate rabbitTemplate;

    private AiAnalysisResultConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AiAnalysisResultConsumer(wsPushService, taskStateRedisService,
                rabbitTemplate, new ObjectMapper(), new QForgeBusinessProperties());
    }

    @Test
    void shouldTruncateErrorMessageBeforePublishingDbWriteBackEvent() {
        String longErrorMessage = "E".repeat(2500);

        consumer.onAiAnalysisResult(new AiAnalysisResultEvent(
                "task-1",
                "question-1",
                "admin",
                false,
                null,
                null,
                null,
                longErrorMessage
        ));

        ArgumentCaptor<DbWriteBackEvent> captor = ArgumentCaptor.forClass(DbWriteBackEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(DbPersistConstants.DB_EXCHANGE),
                eq(DbPersistConstants.ROUTING_DB_PERSIST),
                captor.capture()
        );
        DbWriteBackEvent event = captor.getValue();
        assertEquals("FAILED", event.status());
        assertEquals(2048, event.errorMsg().length());
        assertEquals(longErrorMessage.substring(0, 2048), event.errorMsg());
    }

    @Test
    void shouldTruncateReasoningBeforePublishingDbWriteBackEvent() {
        String longReasoning = "R".repeat(1500);

        consumer.onAiAnalysisResult(new AiAnalysisResultEvent(
                "task-2",
                "question-2",
                "admin",
                true,
                List.of("tagA"),
                new BigDecimal("0.33"),
                longReasoning,
                null
        ));

        ArgumentCaptor<DbWriteBackEvent> captor = ArgumentCaptor.forClass(DbWriteBackEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(DbPersistConstants.DB_EXCHANGE),
                eq(DbPersistConstants.ROUTING_DB_PERSIST),
                captor.capture()
        );
        DbWriteBackEvent event = captor.getValue();
        assertEquals("SUCCESS", event.status());
        assertEquals(1024, event.reasoning().length());
        assertEquals(longReasoning.substring(0, 1024), event.reasoning());
    }
}
