package io.github.kamill7779.qforge.ocr.client;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;
import io.github.kamill7779.qforge.ocr.config.AnswerXmlProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Convert OCR text into answer XML.
 */
@Component
public class AnswerXmlConverter {

    private static final Logger log = LoggerFactory.getLogger(AnswerXmlConverter.class);
    private static final int MAX_RETRIES = 2;

    private static final String SYSTEM_PROMPT = String.join("\n",
            "You are an XML conversion engine for math answer content.",
            "",
            "SCHEMA:",
            "Root: <answer version=\"1\">",
            "Allowed tags: answer, p, image, blank, table, thead, tbody, tr, th, td",
            "",
            "RULES:",
            "1. Output root must be <answer version=\"1\">.",
            "2. Use <p> for answer paragraphs and keep LaTeX inline as $...$.",
            "3. If input contains image placeholders like <image ref=\"fig-N\" bbox=\"...\" />,",
            "   keep the image position and output <image ref=\"fig-N\" /> (remove bbox).",
            "4. Keep mathematical meaning and equations unchanged.",
            "5. Output XML only. No markdown code fences and no explanations."
    );

    private final ZhipuAiClient zhipuAiClient;
    private final AnswerXmlProperties properties;

    public AnswerXmlConverter(ZhipuAiClient zhipuAiClient, AnswerXmlProperties properties) {
        this.zhipuAiClient = zhipuAiClient;
        this.properties = properties;
    }

    public String convertToAnswerXml(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return ocrText;
        }

        log.info("Converting OCR text to answer XML via GLM (model={}, text_len={})",
                properties.getModel(), ocrText.length());

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            String xml = doConvert(ocrText);
            if (xml != null && !xml.isBlank()) {
                log.info("Answer XML conversion complete (xml_len={}, attempt={})", xml.length(), attempt);
                return xml;
            }
            log.warn("GLM returned empty content for answer XML conversion (attempt={}/{})",
                    attempt, MAX_RETRIES);
        }

        throw new RuntimeException("GLM answer XML conversion returned empty content after "
                + MAX_RETRIES + " attempts (model=" + properties.getModel() + ")");
    }

    private String doConvert(String ocrText) {
        ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                .model(properties.getModel())
                .messages(List.of(
                        ChatMessage.builder()
                                .role(ChatMessageRole.SYSTEM.value())
                                .content(SYSTEM_PROMPT)
                                .build(),
                        ChatMessage.builder()
                                .role(ChatMessageRole.USER.value())
                                .content(ocrText)
                                .build()
                ))
                .temperature(properties.getTemperature())
                .maxTokens(properties.getMaxTokens())
                .stream(false)
                .build();

        ChatCompletionResponse response = zhipuAiClient.chat().createChatCompletion(params);
        if (!response.isSuccess()) {
            throw new RuntimeException("GLM answer XML conversion failed: " + response.getMsg());
        }

        Object content = response.getData().getChoices().get(0).getMessage().getContent();
        String xml = content != null ? content.toString().trim() : "";
        return stripCodeFences(xml);
    }

    private String stripCodeFences(String text) {
        String s = text.trim();
        if (!s.startsWith("```")) {
            return s;
        }
        String[] lines = s.split("\n");
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (!line.trim().startsWith("```")) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }
}

