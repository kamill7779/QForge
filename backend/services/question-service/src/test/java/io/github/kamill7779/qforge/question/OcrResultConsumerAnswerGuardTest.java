package io.github.kamill7779.qforge.question;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.question.config.QForgeBusinessProperties;
import io.github.kamill7779.qforge.question.mq.OcrResultConsumer;
import io.github.kamill7779.qforge.question.redis.TaskStateRedisService;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

@ExtendWith(MockitoExtension.class)
class OcrResultConsumerAnswerGuardTest {

    @Mock
    private OcrWsPushService wsPushService;
    @Mock
    private TaskStateRedisService taskStateRedisService;
    @Mock
    private RabbitTemplate rabbitTemplate;
    @Mock
    private QuestionRepository questionRepository;
    @Mock
    private QuestionAssetRepository questionAssetRepository;
    @Mock
    private StringRedisTemplate redis;
    @Mock
    private QForgeBusinessProperties bizProps;

    private OcrResultConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OcrResultConsumer(
                wsPushService,
                taskStateRedisService,
                rabbitTemplate,
                questionRepository,
                questionAssetRepository,
                redis,
                new ObjectMapper(),
                bizProps
        );
    }

    @Test
    void shouldReleaseAnswerOcrGuardOnSuccess() {
        when(taskStateRedisService.getOcrTask("task-1"))
                .thenReturn(Optional.of(Map.of("userId", "admin")));

        consumer.onOcrResult(new OcrTaskResultEvent(
                "task-1",
                "ANSWER_CONTENT",
                "q-1",
                "SUCCESS",
                "<answer><p>ok</p></answer>",
                null,
                null,
                "admin",
                "2026-03-04T10:00:00Z",
                null
        ));

        verify(taskStateRedisService).releaseAnswerOcrGuard("q-1");
    }

    @Test
    void shouldReleaseAnswerOcrGuardOnFailed() {
        when(taskStateRedisService.getOcrTask("task-2"))
                .thenReturn(Optional.of(Map.of("userId", "admin")));

        consumer.onOcrResult(new OcrTaskResultEvent(
                "task-2",
                "ANSWER_CONTENT",
                "q-2",
                "FAILED",
                null,
                "OCR_PROCESSING_ERROR",
                "failed",
                "admin",
                "2026-03-04T10:00:01Z",
                null
        ));

        verify(taskStateRedisService).releaseAnswerOcrGuard("q-2");
    }

    @Test
    void shouldNotReleaseAnswerOcrGuardForStemTask() {
        when(taskStateRedisService.getOcrTask("task-3"))
                .thenReturn(Optional.of(Map.of("userId", "admin")));

        consumer.onOcrResult(new OcrTaskResultEvent(
                "task-3",
                "QUESTION_STEM",
                "q-3",
                "SUCCESS",
                "<stem><p>ok</p></stem>",
                null,
                null,
                "admin",
                "2026-03-04T10:00:02Z",
                null
        ));

        verify(taskStateRedisService, never()).releaseAnswerOcrGuard("q-3");
    }
}
