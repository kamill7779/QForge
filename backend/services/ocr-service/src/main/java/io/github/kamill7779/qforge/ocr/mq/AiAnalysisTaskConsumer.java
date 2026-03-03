package io.github.kamill7779.qforge.ocr.mq;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.AiAnalysisResultEvent;
import io.github.kamill7779.qforge.common.contract.AiAnalysisTaskCreatedEvent;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.ocr.config.ZhipuAiProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisTaskConsumer.class);

        private static final String SYSTEM_PROMPT = String.join("\n",
            "你是教育题目分析助手。",
            "目标：输出2-5个中文知识点标签 + 难度P值(0.00-1.00) + 简短理由。",
            "规则：标签要具体；difficulty保留两位小数；reasoning控制在50字内。",
            "只输出JSON，不要任何解释：",
            "{\"tags\":[\"标签1\",\"标签2\"],\"difficulty\":0.65,\"reasoning\":\"...\"}"
        );
        private static final int DEFAULT_MAX_TOKENS = 1536;
        private static final int MAX_STEM_CHARS = 6000;
        private static final int MAX_SINGLE_ANSWER_CHARS = 1200;
        private static final int MAX_ANSWERS = 4;

    private final ZhipuAiClient zhipuAiClient;
    private final ZhipuAiProperties zhipuAiProperties;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public AiAnalysisTaskConsumer(
            ZhipuAiClient zhipuAiClient,
            ZhipuAiProperties zhipuAiProperties,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper
    ) {
        this.zhipuAiClient = zhipuAiClient;
        this.zhipuAiProperties = zhipuAiProperties;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitTopologyConfig.AI_ANALYSIS_TASK_QUEUE)
    public void onAiAnalysisTask(AiAnalysisTaskCreatedEvent event) {
        log.info("Received AI analysis task for question={}", event.questionUuid());

        try {
            String userPrompt = buildUserPrompt(event);
            int outputMaxTokens = zhipuAiProperties.getMaxTokens() != null
                ? Math.max(256, zhipuAiProperties.getMaxTokens())
                : DEFAULT_MAX_TOKENS;
            float temperature = zhipuAiProperties.getTemperature() != null
                ? Math.max(0f, Math.min(1f, zhipuAiProperties.getTemperature()))
                : 0.2f;

            log.info(
                "AI analysis prompt length for question={} systemChars={}, userChars={}, maxTokens={}",
                event.questionUuid(),
                SYSTEM_PROMPT.length(),
                userPrompt.length(),
                outputMaxTokens
            );

            ChatCompletionCreateParams params = ChatCompletionCreateParams.builder()
                    .model(zhipuAiProperties.getModel())
                    .messages(List.of(
                            ChatMessage.builder()
                                    .role(ChatMessageRole.SYSTEM.value())
                                    .content(SYSTEM_PROMPT)
                                    .build(),
                            ChatMessage.builder()
                                    .role(ChatMessageRole.USER.value())
                                    .content(userPrompt)
                                    .build()
                    ))
                    .temperature(temperature)
                    .maxTokens(outputMaxTokens)
                    .stream(false)
                    .build();

            ChatCompletionResponse response = zhipuAiClient.chat().createChatCompletion(params);

            if (!response.isSuccess()) {
                String errDetail = response.getMsg() != null ? response.getMsg() : "(no message)";
                try {
                    errDetail += " | code=" + response.getCode()
                        + " | data=" + objectMapper.writeValueAsString(response.getData());
                } catch (Exception ignored) {}
                log.error("GLM API call failed for question={}: {}", event.questionUuid(), errDetail);
                publishResult(event, false, null, null, null, "GLM API call failed: " + response.getMsg());
                return;
            }

            if (response.getData() == null || response.getData().getChoices() == null
                    || response.getData().getChoices().isEmpty()) {
                log.error("GLM API returned no choices for question={}, data={}",
                        event.questionUuid(), response.getData());
                publishResult(event, false, null, null, null, "GLM API returned no choices");
                return;
            }

            var choice = response.getData().getChoices().get(0);
            String finishReason = choice.getFinishReason();
            Object content = choice.getMessage().getContent();
            String reasoningContent = choice.getMessage().getReasoningContent();
            String rawContentStr = content != null ? content.toString() : null;

            log.info("AI analysis response for question={} finishReason={} contentType={} rawLen={} reasoningLen={}",
                    event.questionUuid(),
                    finishReason,
                    content != null ? content.getClass().getSimpleName() : "null",
                    rawContentStr != null ? rawContentStr.length() : -1,
                    reasoningContent != null ? reasoningContent.length() : -1);

            String rawJson = rawContentStr != null ? rawContentStr.trim() : "";
            rawJson = stripCodeFences(rawJson);

            // Fallback: reasoning models (GLM-Z1) may put JSON inside reasoningContent
            if (rawJson.isEmpty() && reasoningContent != null && !reasoningContent.isBlank()) {
                log.info("content is empty, trying to extract JSON from reasoningContent for question={}",
                        event.questionUuid());
                String extracted = extractJsonFromText(reasoningContent);
                if (!extracted.isEmpty()) {
                    log.info("Extracted JSON from reasoningContent for question={}: {}", event.questionUuid(), extracted);
                    rawJson = extracted;
                }
            }

            log.info("AI analysis raw response for question={}: {}", event.questionUuid(), rawJson);

            if (rawJson.isEmpty()) {
                // Dump the full message for diagnosis
                String msgDump = "";
                try {
                    msgDump = objectMapper.writeValueAsString(choice.getMessage());
                } catch (Exception ignored) {}
                String hint = "finish_reason=" + finishReason
                    + "; content_class=" + (content != null ? content.getClass().getName() : "null")
                    + "; full_msg=" + msgDump;
                log.warn("AI analysis returned empty content for question={}, hint={}", event.questionUuid(), hint);
                publishResult(event, false, null, null, null,
                        "AI model returned empty response (" + hint + ")");
                return;
            }

            JsonNode node = objectMapper.readTree(rawJson);

            List<String> tags = new ArrayList<>();
            if (node.has("tags") && node.get("tags").isArray()) {
                for (JsonNode tagNode : node.get("tags")) {
                    tags.add(tagNode.asText());
                }
            }

            BigDecimal difficulty = null;
            if (node.has("difficulty")) {
                difficulty = new BigDecimal(node.get("difficulty").asText())
                        .setScale(2, RoundingMode.HALF_UP);
                // Clamp to [0.00, 1.00]
                if (difficulty.compareTo(BigDecimal.ZERO) < 0) {
                    difficulty = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                if (difficulty.compareTo(BigDecimal.ONE) > 0) {
                    difficulty = BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
                }
            }

            String reasoning = node.has("reasoning") ? node.get("reasoning").asText() : "";

            publishResult(event, true, tags, difficulty, reasoning, null);

        } catch (Exception ex) {
            log.error("AI analysis failed for question={}", event.questionUuid(), ex);
            publishResult(event, false, null, null, null, ex.getMessage());
        }
    }

    private String buildUserPrompt(AiAnalysisTaskCreatedEvent event) {
        String stem = truncate(event.stemXml(), MAX_STEM_CHARS);
        int totalAnswers = event.answerTexts() == null ? 0 : event.answerTexts().size();
        List<String> answers = new ArrayList<>();
        if (event.answerTexts() != null) {
            int limit = Math.min(MAX_ANSWERS, event.answerTexts().size());
            for (int i = 0; i < limit; i++) {
                answers.add(truncate(event.answerTexts().get(i), MAX_SINGLE_ANSWER_CHARS));
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("题目内容\n\n");
        sb.append("题干:\n");
        sb.append(stem).append("\n\n");

        if (!answers.isEmpty()) {
            sb.append("答案:\n");
            for (int i = 0; i < answers.size(); i++) {
                sb.append("答案 ").append(i + 1).append(": ").append(answers.get(i)).append("\n");
            }
            if (totalAnswers > answers.size()) {
                sb.append(String.format(Locale.ROOT, "(其余 %d 条答案已截断)\n", totalAnswers - answers.size()));
            }
        }

        return sb.toString();
    }

    private String truncate(String text, int maxChars) {
        String raw = text == null ? "" : text.trim();
        if (raw.length() <= maxChars) {
            return raw;
        }
        int head = (int) Math.floor(maxChars * 0.7);
        int tail = maxChars - head;
        return raw.substring(0, head)
                + "\n...[内容过长，已截断]...\n"
                + raw.substring(raw.length() - tail);
    }

    private void publishResult(
            AiAnalysisTaskCreatedEvent event,
            boolean success,
            List<String> tags,
            BigDecimal difficulty,
            String reasoning,
            String errorMessage
    ) {
        AiAnalysisResultEvent result = new AiAnalysisResultEvent(
                event.questionUuid(),
                event.userId(),
                success,
                tags != null ? tags : List.of(),
                difficulty,
                reasoning,
                errorMessage
        );

        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.AI_EXCHANGE,
                RabbitTopologyConfig.ROUTING_AI_ANALYSIS_RESULT,
                result
        );

        log.info("Published AI analysis result for question={}, success={}", event.questionUuid(), success);
    }

    private String stripCodeFences(String text) {
        String s = text.trim();
        if (s.startsWith("```")) {
            String[] lines = s.split("\n");
            StringBuilder sb = new StringBuilder();
            for (String line : lines) {
                if (!line.trim().startsWith("```")) {
                    sb.append(line).append("\n");
                }
            }
            s = sb.toString().trim();
        }
        return s;
    }

    /**
     * Scans text for the first balanced JSON object { ... } and returns it.
     * Used as fallback when reasoning models (GLM-Z1) embed the final JSON
     * inside reasoning_content instead of content.
     */
    private String extractJsonFromText(String text) {
        int start = text.indexOf('{');
        if (start < 0) return "";
        int depth = 0;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(start, i + 1);
                }
            }
        }
        return "";
    }
}
