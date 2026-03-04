package io.github.kamill7779.qforge.ocr.mq;

import io.github.kamill7779.qforge.common.contract.DbPersistConstants;
import io.github.kamill7779.qforge.common.contract.DbWriteBackEvent;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.common.contract.OcrTaskCreatedEvent;
import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.ocr.client.GlmOcrClient;
import io.github.kamill7779.qforge.ocr.client.ImageRegionCropper;
import io.github.kamill7779.qforge.ocr.client.OcrTextPreprocessor;
import io.github.kamill7779.qforge.ocr.client.StemXmlConverter;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * OCR 任务消费者 —— 接收 {@link OcrTaskCreatedEvent}，调用外部 OCR API 处理，
 * 结果通过 MQ 分别发往 question-service（业务通知）和 persist-service（异步落库）。
 *
 * <p>本消费者 <b>不直接访问数据库</b>，所有 DB 写入委托给 persist-service。</p>
 */
@Component
public class OcrTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(OcrTaskConsumer.class);

    private final GlmOcrClient glmOcrClient;
    private final OcrTextPreprocessor ocrTextPreprocessor;
    private final ImageRegionCropper imageRegionCropper;
    private final StemXmlConverter stemXmlConverter;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OcrTaskConsumer(
            GlmOcrClient glmOcrClient,
            OcrTextPreprocessor ocrTextPreprocessor,
            ImageRegionCropper imageRegionCropper,
            StemXmlConverter stemXmlConverter,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.glmOcrClient = glmOcrClient;
        this.ocrTextPreprocessor = ocrTextPreprocessor;
        this.imageRegionCropper = imageRegionCropper;
        this.stemXmlConverter = stemXmlConverter;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitTopologyConfig.OCR_TASK_QUEUE)
    public void onTaskCreated(OcrTaskCreatedEvent event) {
        log.info("Received OCR task event: taskUuid={}, bizType={}", event.taskUuid(), event.bizType());

        // 通知 persist-service：PROCESSING
        publishDbWriteBack(event.taskUuid(), "PROCESSING", null, null);

        try {
            log.info("Calling GLM OCR for task: {}", event.taskUuid());
            String ocrText = glmOcrClient.recognizeText(event.imageBase64());
            log.info("GLM OCR returned text (len={}) for task: {}",
                    ocrText != null ? ocrText.length() : 0, event.taskUuid());

            String resultText;
            String extractedImagesJson = null;
            if ("QUESTION_STEM".equals(event.bizType())) {
                // 预处理：解析 bbox 标记、清理 Markdown 格式
                OcrTextPreprocessor.PreprocessResult preprocessed =
                        ocrTextPreprocessor.preprocess(ocrText);
                log.info("OCR text preprocessed for task {}: {} bbox regions, cleaned_len={}",
                        event.taskUuid(), preprocessed.bboxRegions().size(),
                        preprocessed.cleanedText() != null ? preprocessed.cleanedText().length() : 0);

                // 图片裁剪：根据 bbox 从原始图片中裁剪子区域
                if (!preprocessed.bboxRegions().isEmpty() && event.imageBase64() != null) {
                    List<ExtractedImage> croppedImages =
                            imageRegionCropper.crop(event.imageBase64(), preprocessed.bboxRegions());
                    if (!croppedImages.isEmpty()) {
                        try {
                            extractedImagesJson = objectMapper.writeValueAsString(croppedImages);
                            log.info("Cropped {} images for task {} (json_len={})",
                                    croppedImages.size(), event.taskUuid(), extractedImagesJson.length());
                        } catch (JsonProcessingException ex) {
                            log.warn("Failed to serialize cropped images for task {}: {}",
                                    event.taskUuid(), ex.getMessage());
                        }
                    }
                }

                log.info("Converting OCR text to stem XML for task: {}", event.taskUuid());
                resultText = stemXmlConverter.convertToStemXml(preprocessed.cleanedText());
            } else {
                resultText = ocrText;
            }

            // 空结果检测：不允许空字符串标记为 SUCCESS
            if (resultText == null || resultText.isBlank()) {
                log.error("OCR result is empty for task {} (bizType={}), marking FAILED",
                        event.taskUuid(), event.bizType());
                publishDbWriteBack(event.taskUuid(), "FAILED", null,
                        "OCR result is empty after conversion");
                publishResult(event, "FAILED", null, "XML_EMPTY",
                        "GLM returned empty content for stem XML conversion", null);
                return;
            }

            // 异步落库 + 业务通知
            publishDbWriteBack(event.taskUuid(), "SUCCESS", resultText, null);
            publishResult(event, "SUCCESS", resultText, null, null, extractedImagesJson);
            log.info("OCR task completed successfully: {}", event.taskUuid());

        } catch (HttpStatusCodeException httpEx) {
            String errorCode = "API_HTTP_" + httpEx.getStatusCode().value();
            log.error("OCR API HTTP error for task {}: status={}, body={}",
                    event.taskUuid(), httpEx.getStatusCode(), httpEx.getResponseBodyAsString(), httpEx);
            String errorMsg = httpEx.getStatusCode() + ": " + httpEx.getResponseBodyAsString();
            publishDbWriteBack(event.taskUuid(), "FAILED", null, errorMsg);
            publishResult(event, "FAILED", null, errorCode, httpEx.getMessage(), null);

        } catch (RuntimeException ex) {
            String errorCode = classifyError(ex);
            log.error("OCR task failed [{}] for task {}: {}", errorCode, event.taskUuid(), ex.getMessage(), ex);
            publishDbWriteBack(event.taskUuid(), "FAILED", null, ex.getMessage());
            publishResult(event, "FAILED", null, errorCode, ex.getMessage(), null);
        }
    }

    private String classifyError(Throwable ex) {
        String msg = ex.getMessage() != null ? ex.getMessage() : "";
        if (msg.contains("apiKey is missing")) return "API_KEY_MISSING";
        if (msg.contains("stem XML conversion failed")) return "XML_CONVERSION_FAILED";
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

    /** 投递异步落库事件到 persist-service。 */
    private void publishDbWriteBack(String taskUuid, String status,
                                     String recognizedText, String errorMsg) {
        DbWriteBackEvent event = DbWriteBackEvent.ocrLocal(taskUuid, status, recognizedText, errorMsg);
        rabbitTemplate.convertAndSend(
                DbPersistConstants.DB_EXCHANGE,
                DbPersistConstants.ROUTING_DB_PERSIST,
                event
        );
    }

    /** 投递 OCR 结果事件到 question-service（业务通知：Redis + WS push）。 */
    private void publishResult(
            OcrTaskCreatedEvent event,
            String status,
            String recognizedText,
            String errorCode,
            String errorMessage,
            String extractedImagesJson
    ) {
        OcrTaskResultEvent resultEvent = new OcrTaskResultEvent(
                event.taskUuid(),
                event.bizType(),
                event.bizId(),
                status,
                recognizedText,
                errorCode,
                errorMessage,
                event.requestUser(),
                Instant.now().toString(),
                extractedImagesJson
        );
        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.OCR_EXCHANGE,
                RabbitTopologyConfig.ROUTING_TASK_RESULT,
                resultEvent
        );
    }
}
