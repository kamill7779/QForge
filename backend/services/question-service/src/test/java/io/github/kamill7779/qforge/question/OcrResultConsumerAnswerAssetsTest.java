package io.github.kamill7779.qforge.question;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
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
class OcrResultConsumerAnswerAssetsTest {

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
    void shouldCacheAnswerExtractedImagesToRedisInsteadOfQuestionAssetTable() {
        when(taskStateRedisService.getOcrTask("task-ans-1"))
                .thenReturn(Optional.of(Map.of("userId", "admin")));

        consumer.onOcrResult(new OcrTaskResultEvent(
                "task-ans-1",
                "ANSWER_CONTENT",
                "q-1",
                "SUCCESS",
                "<answer version=\"1\"><p>ok</p><image ref=\"a92f6c03-img-1\" /></answer>",
                null,
                null,
                "admin",
                "2026-03-04T10:00:00Z",
                "[{\"refKey\":\"a92f6c03-img-1\",\"imageBase64\":\"AAA\",\"mimeType\":\"image/png\"}]"
        ));

        verify(taskStateRedisService).saveAnswerOcrAssets(eq("q-1"), anyList());
        verify(questionAssetRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}

