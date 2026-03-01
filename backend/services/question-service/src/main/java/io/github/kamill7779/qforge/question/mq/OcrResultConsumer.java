package io.github.kamill7779.qforge.question.mq;

import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.entity.QuestionOcrTask;
import io.github.kamill7779.qforge.question.repository.QuestionOcrTaskRepository;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.util.Map;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OcrResultConsumer {

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
        QuestionOcrTask task = questionOcrTaskRepository.findByTaskUuid(event.taskUuid()).orElse(null);
        if (task == null) {
            // The result event may arrive slightly earlier than local task mapping persistence.
            throw new ImmediateRequeueAmqpException("Question OCR task mapping not ready yet");
        }

        task.setStatus(event.status());
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
