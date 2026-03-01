package io.github.kamill7779.qforge.ocr.mq;

import io.github.kamill7779.qforge.common.contract.OcrTaskCreatedEvent;
import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.ocr.client.GlmOcrClient;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.ocr.entity.OcrTask;
import io.github.kamill7779.qforge.ocr.repository.OcrTaskRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OcrTaskConsumer {

    private final OcrTaskRepository ocrTaskRepository;
    private final GlmOcrClient glmOcrClient;
    private final RabbitTemplate rabbitTemplate;

    public OcrTaskConsumer(
            OcrTaskRepository ocrTaskRepository,
            GlmOcrClient glmOcrClient,
            RabbitTemplate rabbitTemplate
    ) {
        this.ocrTaskRepository = ocrTaskRepository;
        this.glmOcrClient = glmOcrClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitTopologyConfig.OCR_TASK_QUEUE)
    @Transactional
    public void onTaskCreated(OcrTaskCreatedEvent event) {
        Optional<OcrTask> optional = ocrTaskRepository.findByTaskUuid(event.taskUuid());
        if (optional.isEmpty()) {
            // Create flow and consumer can race; requeue to wait for DB row visibility.
            throw new ImmediateRequeueAmqpException("OCR task row not ready yet");
        }

        OcrTask task = optional.get();
        if (!"PENDING".equals(task.getStatus())) {
            return;
        }

        task.setStatus("PROCESSING");
        ocrTaskRepository.save(task);

        try {
            String recognizedText = glmOcrClient.recognizeText(task.getImageBase64());
            task.setStatus("SUCCESS");
            task.setRecognizedText(recognizedText);
            task.setErrorMsg(null);
            ocrTaskRepository.save(task);
            publishResult(task, "SUCCESS", recognizedText, null, null);
        } catch (Exception ex) {
            task.setStatus("FAILED");
            task.setErrorMsg(ex.getMessage());
            ocrTaskRepository.save(task);
            publishResult(task, "FAILED", null, "OCR_PROCESSING_ERROR", ex.getMessage());
        }
    }

    private void publishResult(
            OcrTask task,
            String status,
            String recognizedText,
            String errorCode,
            String errorMessage
    ) {
        OcrTaskResultEvent resultEvent = new OcrTaskResultEvent(
                task.getTaskUuid(),
                task.getBizType(),
                task.getBizId(),
                status,
                recognizedText,
                errorCode,
                errorMessage,
                task.getRequestUser(),
                Instant.now().toString()
        );
        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.OCR_EXCHANGE,
                RabbitTopologyConfig.ROUTING_TASK_RESULT,
                resultEvent
        );
    }
}
