package io.github.kamill7779.qforge.question.mq;

import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.entity.QuestionOcrTask;
import io.github.kamill7779.qforge.question.redis.TaskStateRedisService;
import io.github.kamill7779.qforge.question.repository.QuestionOcrTaskRepository;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OcrResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(OcrResultConsumer.class);

    private final QuestionOcrTaskRepository questionOcrTaskRepository;
    private final OcrWsPushService ocrWsPushService;
    private final TaskStateRedisService taskStateRedisService;

    public OcrResultConsumer(
            QuestionOcrTaskRepository questionOcrTaskRepository,
            OcrWsPushService ocrWsPushService,
            TaskStateRedisService taskStateRedisService
    ) {
        this.questionOcrTaskRepository = questionOcrTaskRepository;
        this.ocrWsPushService = ocrWsPushService;
        this.taskStateRedisService = taskStateRedisService;
    }

    @RabbitListener(queues = RabbitTopologyConfig.OCR_RESULT_QUESTION_QUEUE)
    @Transactional
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

        // ---- 3. DB 持久化（可能 DB 行已可见，也可能还不可见；尽力而为） ----
        QuestionOcrTask task = questionOcrTaskRepository.findByTaskUuid(event.taskUuid()).orElse(null);
        if (task != null) {
            String localStatus = "SUCCESS".equals(event.status()) ? "CONFIRMED" : event.status();
            task.setStatus(localStatus);
            task.setRecognizedText(event.recognizedText());
            task.setErrorMsg(event.errorMessage());
            questionOcrTaskRepository.save(task);
            if (requestUser == null) {
                requestUser = task.getRequestUser();
            }
        } else {
            log.warn("OCR task DB row not found for taskUuid={}; Redis state used for WS push", event.taskUuid());
        }

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
