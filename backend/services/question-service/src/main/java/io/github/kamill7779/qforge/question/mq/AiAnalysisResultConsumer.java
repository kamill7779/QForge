package io.github.kamill7779.qforge.question.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.AiAnalysisResultEvent;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.dto.DbWriteBackEvent;
import io.github.kamill7779.qforge.question.redis.TaskStateRedisService;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisResultConsumer {

    private static final int MAX_REASONING_LENGTH = 1024;
    private static final int MAX_ERROR_MESSAGE_LENGTH = 2048;

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisResultConsumer.class);

    private final OcrWsPushService wsPushService;
    private final TaskStateRedisService taskStateRedisService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AiAnalysisResultConsumer(OcrWsPushService wsPushService,
                                    TaskStateRedisService taskStateRedisService,
                                    RabbitTemplate rabbitTemplate,
                                    ObjectMapper objectMapper) {
        this.wsPushService = wsPushService;
        this.taskStateRedisService = taskStateRedisService;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitTopologyConfig.AI_ANALYSIS_RESULT_QUEUE)
    public void onAiAnalysisResult(AiAnalysisResultEvent event) {
        log.info("Received AI analysis result for question={}, taskUuid={}, success={}",
                event.questionUuid(), event.taskUuid(), event.success());
        try {
        // ---- Redis 热状态先行更新 ----
        if (event.taskUuid() != null) {
            if (event.success()) {
                String tagsJson;
                try {
                    tagsJson = objectMapper.writeValueAsString(
                            event.suggestedTags() != null ? event.suggestedTags() : List.of());
                } catch (Exception e) {
                    tagsJson = "[]";
                }
                taskStateRedisService.completeAiTask(event.taskUuid(), tagsJson,
                        event.suggestedDifficulty(), event.reasoning());
            } else {
                taskStateRedisService.failAiTask(event.taskUuid(), event.errorMessage());
            }
        }

        // ---- 投递异步落库写回事件（MQ 队列 + 自动重试，不阻塞主流程）----
        if (event.taskUuid() != null) {
            String tagsJsonForDb = null;
            if (event.success()) {
                try {
                    tagsJsonForDb = objectMapper.writeValueAsString(
                            event.suggestedTags() != null ? event.suggestedTags() : List.of());
                } catch (Exception ignored) {
                    tagsJsonForDb = "[]";
                }
            }
            DbWriteBackEvent writeBack = new DbWriteBackEvent(
                    "AI",
                    event.taskUuid(),
                    event.questionUuid(),
                    event.success() ? "SUCCESS" : "FAILED",
                    event.userId(),
                    null, // bizType: AI 任务无需
                    tagsJsonForDb,
                    event.suggestedDifficulty(),
                    trimToColumnSize(event.reasoning(), MAX_REASONING_LENGTH, "reasoning", event.taskUuid()),
                    null, // recognizedText: OCR 专属
                    trimToColumnSize(event.errorMessage(), MAX_ERROR_MESSAGE_LENGTH, "error_msg", event.taskUuid())
            );
            rabbitTemplate.convertAndSend(
                    RabbitTopologyConfig.DB_EXCHANGE,
                    RabbitTopologyConfig.ROUTING_DB_PERSIST,
                    writeBack
            );
            log.info("Published DbWriteBackEvent for AI taskUuid={} status={}",
                    event.taskUuid(), writeBack.status());
        }

        // ---- WebSocket push (unchanged) ----
        if (event.success()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("questionUuid", event.questionUuid());
            payload.put("taskUuid", event.taskUuid());
            payload.put("suggestedTags", event.suggestedTags());
            payload.put("suggestedDifficulty", event.suggestedDifficulty());
            payload.put("reasoning", event.reasoning());
            wsPushService.push(event.userId(), "ai.analysis.succeeded", payload);
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("questionUuid", event.questionUuid());
            payload.put("taskUuid", event.taskUuid());
            payload.put("errorMessage", event.errorMessage() == null ? "Unknown error" : event.errorMessage());
            wsPushService.push(event.userId(), "ai.analysis.failed", payload);
            }
        } catch (Exception ex) {
            log.error("Failed to process AI analysis result for question={}, taskUuid={}: {}",
                    event.questionUuid(), event.taskUuid(), ex.getMessage(), ex);
        }
    }

    private String trimToColumnSize(String value, int maxLength, String columnName, String taskUuid) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        log.warn("Truncating {} for taskUuid={} from {} to {} characters",
                columnName, taskUuid, value.length(), maxLength);
        return value.substring(0, maxLength);
    }
}
