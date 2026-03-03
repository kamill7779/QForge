package io.github.kamill7779.qforge.question.mq;

import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.entity.QuestionOcrTask;
import io.github.kamill7779.qforge.question.repository.QuestionOcrTaskRepository;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.util.Map;
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

    public OcrResultConsumer(
            QuestionOcrTaskRepository questionOcrTaskRepository,
            OcrWsPushService ocrWsPushService
    ) {
        this.questionOcrTaskRepository = questionOcrTaskRepository;
        this.ocrWsPushService = ocrWsPushService;
    }

    @RabbitListener(queues = RabbitTopologyConfig.OCR_RESULT_QUESTION_QUEUE)
    @Transactional
    public void onOcrResult(OcrTaskResultEvent event) {
        // Retry up to 3 times waiting for DB row to be visible (event may arrive before TX commit)
        QuestionOcrTask task = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            task = questionOcrTaskRepository.findByTaskUuid(event.taskUuid()).orElse(null);
            if (task != null) break;
            log.warn("Question OCR task mapping not ready yet (attempt {}/3), waiting 1s: {}", attempt, event.taskUuid());
            try { Thread.sleep(1000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }
        if (task == null) {
            log.error("Question OCR task mapping still not found after 3 attempts, discarding: {}", event.taskUuid());
            return;
        }

        // Transition to CONFIRMED when OCR succeeds; retain FAILED otherwise.
        String localStatus = "SUCCESS".equals(event.status()) ? "CONFIRMED" : event.status();
        task.setStatus(localStatus);
        task.setRecognizedText(event.recognizedText());
        task.setErrorMsg(event.errorMessage());
        questionOcrTaskRepository.save(task);

        if ("SUCCESS".equals(event.status())) {
            ocrWsPushService.push(task.getRequestUser(), "ocr.task.succeeded", Map.of(
                    "taskUuid", event.taskUuid(),
                    "bizType", event.bizType(),
                    "bizId", event.bizId(),
                    "recognizedText", event.recognizedText() == null ? "" : event.recognizedText()
            ));
        } else if ("FAILED".equals(event.status())) {
            ocrWsPushService.push(task.getRequestUser(), "ocr.task.failed", Map.of(
                    "taskUuid", event.taskUuid(),
                    "bizType", event.bizType(),
                    "bizId", event.bizId(),
                    "errorMessage", event.errorMessage() == null ? "Unknown error" : event.errorMessage()
            ));
        }
    }
}
