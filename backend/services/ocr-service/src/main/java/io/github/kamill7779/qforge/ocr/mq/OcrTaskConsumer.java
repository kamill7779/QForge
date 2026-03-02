package io.github.kamill7779.qforge.ocr.mq;

import io.github.kamill7779.qforge.common.contract.OcrTaskCreatedEvent;
import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.ocr.client.GlmOcrClient;
import io.github.kamill7779.qforge.ocr.client.StemXmlConverter;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.ocr.entity.OcrTask;
import io.github.kamill7779.qforge.ocr.repository.OcrTaskRepository;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.ImmediateRequeueAmqpException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.HttpStatusCodeException;

@Component
public class OcrTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(OcrTaskConsumer.class);

    private final OcrTaskRepository ocrTaskRepository;
    private final GlmOcrClient glmOcrClient;
    private final StemXmlConverter stemXmlConverter;
    private final RabbitTemplate rabbitTemplate;

    public OcrTaskConsumer(
            OcrTaskRepository ocrTaskRepository,
            GlmOcrClient glmOcrClient,
            StemXmlConverter stemXmlConverter,
            RabbitTemplate rabbitTemplate
    ) {
        this.ocrTaskRepository = ocrTaskRepository;
        this.glmOcrClient = glmOcrClient;
        this.stemXmlConverter = stemXmlConverter;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = RabbitTopologyConfig.OCR_TASK_QUEUE)
    @Transactional
    public void onTaskCreated(OcrTaskCreatedEvent event) {
        log.info("Received OCR task event: taskUuid={}, bizType={}", event.taskUuid(), event.bizType());

        Optional<OcrTask> optional = ocrTaskRepository.findByTaskUuid(event.taskUuid());
        if (optional.isEmpty()) {
            log.warn("OCR task row not ready yet, requeuing: {}", event.taskUuid());
            throw new ImmediateRequeueAmqpException("OCR task row not ready yet");
        }

        OcrTask task = optional.get();
        if (!"PENDING".equals(task.getStatus())) {
            log.info("OCR task already processed (status={}), skipping: {}", task.getStatus(), task.getTaskUuid());
            return;
        }

        task.setStatus("PROCESSING");
        ocrTaskRepository.save(task);

        try {
            log.info("Calling GLM OCR for task: {}", task.getTaskUuid());
            String ocrText = glmOcrClient.recognizeText(task.getImageBase64());
            log.info("GLM OCR returned text (len={}) for task: {}", ocrText != null ? ocrText.length() : 0, task.getTaskUuid());

            String resultText;
            if ("QUESTION_STEM".equals(task.getBizType())) {
                log.info("Converting OCR text to stem XML for task: {}", task.getTaskUuid());
                resultText = stemXmlConverter.convertToStemXml(ocrText);
            } else {
                resultText = ocrText;
            }

            task.setStatus("SUCCESS");
            task.setRecognizedText(resultText);
            task.setErrorMsg(null);
            ocrTaskRepository.save(task);
            publishResult(task, "SUCCESS", resultText, null, null);
            log.info("OCR task completed successfully: {}", task.getTaskUuid());
        } catch (HttpStatusCodeException httpEx) {
            String errorCode = "API_HTTP_" + httpEx.getStatusCode().value();
            log.error("OCR API HTTP error for task {}: status={}, body={}",
                    task.getTaskUuid(), httpEx.getStatusCode(), httpEx.getResponseBodyAsString(), httpEx);
            task.setStatus("FAILED");
            task.setErrorMsg(httpEx.getStatusCode() + ": " + httpEx.getResponseBodyAsString());
            ocrTaskRepository.save(task);
            publishResult(task, "FAILED", null, errorCode, httpEx.getMessage());
        } catch (RuntimeException ex) {
            String errorCode = classifyError(ex);
            log.error("OCR task failed [{}] for task {}: {}", errorCode, task.getTaskUuid(), ex.getMessage(), ex);
            task.setStatus("FAILED");
            task.setErrorMsg(ex.getMessage());
            ocrTaskRepository.save(task);
            publishResult(task, "FAILED", null, errorCode, ex.getMessage());
        }
    }

    private String classifyError(Throwable ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("apiKey is missing")) return "API_KEY_MISSING";
        if (msg.contains("stem XML conversion failed")) return "XML_CONVERSION_FAILED";
        // Check cause chain for network errors
        Throwable cursor = ex;
        while (cursor != null) {
            if (cursor instanceof java.net.ConnectException
                    || cursor instanceof java.net.UnknownHostException
                    || cursor instanceof java.net.SocketTimeoutException
                    || cursor instanceof java.net.NoRouteToHostException) {
                return "NETWORK_ERROR";
            }
            cursor = cursor.getCause();
        }
        return "OCR_PROCESSING_ERROR";
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
        // Delay publishing until after the transaction commits to ensure DB changes are visible.
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    rabbitTemplate.convertAndSend(
                            RabbitTopologyConfig.OCR_EXCHANGE,
                            RabbitTopologyConfig.ROUTING_TASK_RESULT,
                            resultEvent
                    );
                }
            });
        } else {
            rabbitTemplate.convertAndSend(
                    RabbitTopologyConfig.OCR_EXCHANGE,
                    RabbitTopologyConfig.ROUTING_TASK_RESULT,
                    resultEvent
            );
        }
    }
}
