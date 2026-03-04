package io.github.kamill7779.qforge.ocr.mq;

import io.github.kamill7779.qforge.common.contract.DbPersistConstants;
import io.github.kamill7779.qforge.common.contract.DbWriteBackEvent;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.common.contract.OcrTaskCreatedEvent;
import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.ocr.client.AnswerXmlConverter;
import io.github.kamill7779.qforge.ocr.client.GlmOcrClient;
import io.github.kamill7779.qforge.ocr.client.ImageRegionCropper;
import io.github.kamill7779.qforge.ocr.client.OcrTextPreprocessor;
import io.github.kamill7779.qforge.ocr.client.StemXmlConverter;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.xml.sax.InputSource;

/**
 * OCR 任务消费者 —— 接收 {@link OcrTaskCreatedEvent}，调用外部 OCR API 处理，
 * 结果通过 MQ 分别发往 question-service（业务通知）和 persist-service（异步落库）。
 *
 * <p>本消费者 <b>不直接访问数据库</b>，所有 DB 写入委托给 persist-service。</p>
 */
@Component
public class OcrTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(OcrTaskConsumer.class);
    private static final Pattern ANSWER_IMAGE_TOKEN_PATTERN = Pattern.compile(
            "(?i)<image\\b[^>]*\\bref\\s*=\\s*['\"]([^'\"]+)['\"][^>]*/?>|!\\[[^\\]]*]\\(page=\\d+,bbox=\\[[^\\]]+\\]\\)");
    private static final Pattern ANSWER_BBOX_MARKDOWN_PATTERN = Pattern.compile(
            "!\\[[^\\]]*]\\(page=\\d+,bbox=\\[[^\\]]+\\]\\)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ANSWER_BBOX_ATTR_PATTERN = Pattern.compile(
            "\\s+bbox\\s*=\\s*(\"[^\"]*\"|'[^']*')",
            Pattern.CASE_INSENSITIVE);

    private final GlmOcrClient glmOcrClient;
    private final OcrTextPreprocessor ocrTextPreprocessor;
    private final ImageRegionCropper imageRegionCropper;
    private final StemXmlConverter stemXmlConverter;
    private final AnswerXmlConverter answerXmlConverter;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public OcrTaskConsumer(
            GlmOcrClient glmOcrClient,
            OcrTextPreprocessor ocrTextPreprocessor,
            ImageRegionCropper imageRegionCropper,
            StemXmlConverter stemXmlConverter,
            AnswerXmlConverter answerXmlConverter,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.glmOcrClient = glmOcrClient;
        this.ocrTextPreprocessor = ocrTextPreprocessor;
        this.imageRegionCropper = imageRegionCropper;
        this.stemXmlConverter = stemXmlConverter;
        this.answerXmlConverter = answerXmlConverter;
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
            if ("QUESTION_STEM".equals(event.bizType()) || "ANSWER_CONTENT".equals(event.bizType())) {
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
                    if ("ANSWER_CONTENT".equals(event.bizType())) {
                        croppedImages = remapAnswerImages(croppedImages, event.taskUuid());
                    }
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

                if ("QUESTION_STEM".equals(event.bizType())) {
                    log.info("Converting OCR text to stem XML for task: {}", event.taskUuid());
                    resultText = stemXmlConverter.convertToStemXml(preprocessed.cleanedText());
                } else {
                    log.info("Converting OCR text to answer XML for task: {}", event.taskUuid());
                    String converted = answerXmlConverter.convertToAnswerXml(preprocessed.cleanedText());
                    resultText = ensureAnswerXml(converted, preprocessed.cleanedText(), event.taskUuid());
                }
            } else {
                resultText = ocrText;
            }

            // 空结果检测：不允许空字符串标记为 SUCCESS
            if (resultText == null || resultText.isBlank()) {
                log.error("OCR result is empty for task {} (bizType={}), marking FAILED",
                        event.taskUuid(), event.bizType());
                String conversionKind = "ANSWER_CONTENT".equals(event.bizType()) ? "answer XML" : "stem XML";
                publishDbWriteBack(event.taskUuid(), "FAILED", null,
                        "OCR result is empty after conversion");
                publishResult(event, "FAILED", null, "XML_EMPTY",
                        "GLM returned empty content for " + conversionKind + " conversion", null);
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
        if (msg.contains("answer XML conversion failed")) return "XML_CONVERSION_FAILED";
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

    private String ensureAnswerXml(String converted, String preprocessedText, String taskUuid) {
        String candidate = converted == null ? "" : converted.trim();
        if (isValidAnswerXml(candidate) && !ANSWER_BBOX_MARKDOWN_PATTERN.matcher(candidate).find()) {
            return remapAnswerRefsInXml(stripBboxAttrs(candidate), taskUuid);
        }

        log.warn("Answer XML conversion returned non-compliant content for task {}, fallback to deterministic XML",
                taskUuid);
        String fallback = buildAnswerXmlFromPreprocessed(preprocessedText, taskUuid);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }

        // Last resort: keep candidate but normalize refs where possible.
        return remapAnswerRefsInXml(stripBboxAttrs(candidate), taskUuid);
    }

    private boolean isValidAnswerXml(String xml) {
        if (xml == null || xml.isBlank() || !xml.trim().startsWith("<answer")) {
            return false;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            var doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
            return doc != null
                    && doc.getDocumentElement() != null
                    && "answer".equalsIgnoreCase(doc.getDocumentElement().getTagName());
        } catch (Exception ignore) {
            return false;
        }
    }

    private String buildAnswerXmlFromPreprocessed(String text, String taskUuid) {
        if (text == null || text.isBlank()) {
            return text;
        }
        List<AnswerToken> tokens = new ArrayList<>();
        String normalized = text.replace("\r\n", "\n");
        Matcher matcher = ANSWER_IMAGE_TOKEN_PATTERN.matcher(normalized);
        int cursor = 0;
        int fallbackImageIndex = 1;
        while (matcher.find()) {
            appendAnswerTextTokens(tokens, normalized.substring(cursor, matcher.start()));
            String ref = matcher.group(1);
            if (ref == null || ref.isBlank()) {
                ref = "fig-" + fallbackImageIndex;
            }
            tokens.add(AnswerToken.image(ref.trim()));
            fallbackImageIndex += 1;
            cursor = matcher.end();
        }
        appendAnswerTextTokens(tokens, normalized.substring(cursor));

        if (tokens.isEmpty()) {
            appendAnswerTextTokens(tokens, normalized);
        }
        if (tokens.isEmpty()) {
            return "";
        }

        StringBuilder xml = new StringBuilder("<answer version=\"1\">");
        for (AnswerToken token : tokens) {
            if (token.image()) {
                xml.append("<image ref=\"")
                        .append(escapeXml(token.value()))
                        .append("\" />");
            } else {
                xml.append("<p>")
                        .append(escapeXml(token.value()))
                        .append("</p>");
            }
        }
        xml.append("</answer>");
        return remapAnswerRefsInXml(stripBboxAttrs(xml.toString()), taskUuid);
    }

    private void appendAnswerTextTokens(List<AnswerToken> tokens, String segment) {
        if (segment == null || segment.isBlank()) {
            return;
        }
        String[] lines = segment.replace("\r\n", "\n").split("\\n+");
        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (!trimmed.isBlank()) {
                tokens.add(AnswerToken.text(trimmed));
            }
        }
    }

    private String stripBboxAttrs(String xml) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }
        return ANSWER_BBOX_ATTR_PATTERN.matcher(xml).replaceAll("");
    }

    private String escapeXml(String value) {
        String text = value == null ? "" : value;
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private record AnswerToken(boolean image, String value) {
        private static AnswerToken text(String value) {
            return new AnswerToken(false, value);
        }

        private static AnswerToken image(String value) {
            return new AnswerToken(true, value);
        }
    }

    private List<ExtractedImage> remapAnswerImages(List<ExtractedImage> images, String taskUuid) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<ExtractedImage> mapped = new ArrayList<>(images.size());
        for (int i = 0; i < images.size(); i++) {
            ExtractedImage src = images.get(i);
            mapped.add(new ExtractedImage(
                    buildAnswerRef(taskUuid, i + 1),
                    src.imageBase64(),
                    src.mimeType()
            ));
        }
        return mapped;
    }

    private String remapAnswerRefsInXml(String xml, String taskUuid) {
        if (xml == null || xml.isBlank()) {
            return xml;
        }
        String result = xml;
        for (int i = 1; i <= 64; i++) {
            String from = "fig-" + i;
            String to = buildAnswerRef(taskUuid, i);
            result = result.replace("ref=\"" + from + "\"", "ref=\"" + to + "\"");
            result = result.replace("ref='" + from + "'", "ref='" + to + "'");
        }
        return result;
    }

    private String buildAnswerRef(String taskUuid, int index) {
        String compact = taskUuid == null ? "" : taskUuid.replace("-", "").toLowerCase(Locale.ROOT);
        if (compact.length() < 8) {
            compact = (compact + "00000000").substring(0, 8);
        } else {
            compact = compact.substring(0, 8);
        }
        return "a" + compact + "-img-" + index;
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
