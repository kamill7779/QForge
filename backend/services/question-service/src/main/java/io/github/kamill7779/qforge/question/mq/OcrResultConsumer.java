package io.github.kamill7779.qforge.question.mq;

import io.github.kamill7779.qforge.common.contract.DbPersistConstants;
import io.github.kamill7779.qforge.common.contract.DbWriteBackEvent;
import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.redis.TaskStateRedisService;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class OcrResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(OcrResultConsumer.class);

    private final OcrWsPushService ocrWsPushService;
    private final TaskStateRedisService taskStateRedisService;
    private final RabbitTemplate rabbitTemplate;

    public OcrResultConsumer(
            OcrWsPushService ocrWsPushService,
            TaskStateRedisService taskStateRedisService,
            RabbitTemplate rabbitTemplate
    ) {
        this.ocrWsPushService = ocrWsPushService;
        this.taskStateRedisService = taskStateRedisService;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitTopologyConfig.OCR_RESULT_QUESTION_QUEUE)
    public void onOcrResult(OcrTaskResultEvent event) {
        log.info("Received OCR result for taskUuid={}, status={}", event.taskUuid(), event.status());

        // ---- 1. Redis 热状态更新 ----
        if ("SUCCESS".equals(event.status())) {
            taskStateRedisService.completeOcrTask(event.taskUuid(), event.recognizedText());
        } else {
            taskStateRedisService.failOcrTask(event.taskUuid(), event.errorMessage());
        }

        // ---- 2. 从 Redis 获取任务映射（替代旧的 sleep-retry DB 查询）----
        String requestUser = null;
        Optional<Map<String, Object>> redisState = taskStateRedisService.getOcrTask(event.taskUuid());
        if (redisState.isPresent()) {
            requestUser = (String) redisState.get().get("userId");
        }

        // ---- 3. 投递异步落库写回事件（MQ 队列 + 自动重试，不阻塞主流程）----
        DbWriteBackEvent writeBack = DbWriteBackEvent.ocr(
                event.taskUuid(),
                event.bizId(),
                "SUCCESS".equals(event.status()) ? "CONFIRMED" : event.status(),
                requestUser,
                event.bizType(),
                event.recognizedText(),
                event.errorMessage()
        );
        rabbitTemplate.convertAndSend(
                DbPersistConstants.DB_EXCHANGE,
                DbPersistConstants.ROUTING_DB_PERSIST,
                writeBack
        );
        log.info("Published DbWriteBackEvent for OCR taskUuid={} status={}",
                event.taskUuid(), writeBack.status());

        // ---- 4. WebSocket 推送 ----
        if (requestUser == null) {
            log.error("Cannot determine requestUser for taskUuid={}, skipping WS push", event.taskUuid());
            return;
        }

        if ("SUCCESS".equals(event.status())) {
            ocrWsPushService.push(requestUser, "ocr.task.succeeded", Map.of(
                    "taskUuid", event.taskUuid(),
                    "bizType", event.bizType(),
                    "bizId", event.bizId(),
                    "recognizedText", event.recognizedText() == null ? "" : event.recognizedText()
            ));
        } else if ("FAILED".equals(event.status())) {
            ocrWsPushService.push(requestUser, "ocr.task.failed", Map.of(
                    "taskUuid", event.taskUuid(),
                    "bizType", event.bizType(),
                    "bizId", event.bizId(),
                    "errorMessage", event.errorMessage() == null ? "Unknown error" : event.errorMessage()
            ));
        }
    }
}
