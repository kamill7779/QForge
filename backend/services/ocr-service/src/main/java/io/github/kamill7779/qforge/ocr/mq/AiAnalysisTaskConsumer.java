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
import io.github.kamill7779.qforge.common.contract.DbPersistConstants;
import io.github.kamill7779.qforge.common.contract.DbWriteBackEvent;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.ocr.config.ZhipuAiProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisTaskConsumer.class);

        private static final String RESULT_START = "##RESULT_START##";
        private static final String RESULT_END   = "##RESULT_END##";

        private static final String SYSTEM_PROMPT =
            "你是教育评估专家。请严格按如下格式输出，不得先输出分析过程：\n" +
            "##RESULT_START##\n" +
            "{\"tags\":[\"标签1\",\"标签2\"],\"difficulty\":0.55,\"reasoning\":\"30字内结论\"}\n" +
            "##RESULT_END##\n" +
            "标签：推荐2-5个知识点（中文，粒度如\"二次函数\"、\"解三角形\"，禁用\"数学\"等过宽表述）。\n" +
            "P值校准（通过率0.00-1.00）：0.8+极简单, 0.6-0.8简单, 0.4-0.6中等, 0.2-0.4偏难, 0.05-0.2竞赛/压轴, <0.05联赛级。\n" +
            "示例：立体几何综合→0.35, 二次函数求值→0.60, 基础填空→0.75, 竞赛组合→0.10。\n" +
            "reasoning必须是结论句（如\"立体几何综合，空间想象要求高\"），禁止使用\"任务一\"\"**标签**\"等标题或Markdown。\n" +
            "注意：##RESULT_START## 和 ##RESULT_END## 是必须输出的定界符，JSON放在两者之间。";
        private static final int DEFAULT_MAX_TOKENS = 4096;
        private static final int MAX_STEM_CHARS = 8000;
        private static final int MAX_SINGLE_ANSWER_CHARS = 2000;
        private static final int MAX_ANSWERS = 6;
        private static final Pattern DIFFICULTY_PATTERN = Pattern.compile("(?:\\bP\\b|difficulty)\\s*[:=]\\s*([01](?:\\.\\d{1,4})?)", Pattern.CASE_INSENSITIVE);
        private static final Pattern DIFFICULTY_PATTERN_FALLBACK = Pattern.compile("\\bP\\b[^\\d]{0,40}([01](?:\\.\\d{1,4})?)", Pattern.CASE_INSENSITIVE);
        private static final Pattern QUOTED_TAG_PATTERN = Pattern.compile("[\"“](.{1,20}?)[\"”]");

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
        log.info("Received AI analysis task for question={}, taskUuid={}", event.questionUuid(), event.taskUuid());

        // 异步落库：PROCESSING 初始化
        rabbitTemplate.convertAndSend(DbPersistConstants.DB_EXCHANGE, DbPersistConstants.ROUTING_DB_PERSIST,
                DbWriteBackEvent.aiLocalProcessing(event.taskUuid(), event.questionUuid(),
                        event.userId(), zhipuAiProperties.getModel(), null));

        String userPrompt = null;
        String rawResponseForAudit = null;
        try {
            userPrompt = buildUserPrompt(event);

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
                String failMsg1 = "GLM API call failed: " + response.getMsg();
                rabbitTemplate.convertAndSend(DbPersistConstants.DB_EXCHANGE, DbPersistConstants.ROUTING_DB_PERSIST,
                        DbWriteBackEvent.aiLocalFailed(event.taskUuid(), event.questionUuid(),
                                failMsg1, userPrompt, rawResponseForAudit));
                publishResult(event, false, null, null, null, failMsg1);
                return;
            }

            if (response.getData() == null || response.getData().getChoices() == null
                    || response.getData().getChoices().isEmpty()) {
                log.error("GLM API returned no choices for question={}, data={}",
                        event.questionUuid(), response.getData());
                rabbitTemplate.convertAndSend(DbPersistConstants.DB_EXCHANGE, DbPersistConstants.ROUTING_DB_PERSIST,
                        DbWriteBackEvent.aiLocalFailed(event.taskUuid(), event.questionUuid(),
                                "GLM API returned no choices", userPrompt, rawResponseForAudit));
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

            // Primary: extract JSON between ##RESULT_START## / ##RESULT_END## delimiters
            String delimited = extractByDelimiter(rawJson);
            if (!delimited.isEmpty()) {
                log.info("Extracted JSON via delimiter from content for question={}", event.questionUuid());
                rawJson = delimited;
            } else {
                rawJson = stripCodeFences(rawJson);
                rawJson = unescapeDoubleEncodedJson(rawJson);
            }

            // Fallback 1: reasoning models (GLM-Z1) may put JSON inside reasoningContent
            if (rawJson.isEmpty() && reasoningContent != null && !reasoningContent.isBlank()) {
                log.info("content is empty, trying to extract JSON from reasoningContent for question={}",
                        event.questionUuid());
                // Try delimiter first, then validated brace-scan
                String extracted = extractByDelimiter(reasoningContent);
                if (extracted.isEmpty()) {
                    extracted = extractJsonFromText(reasoningContent);
                }
                if (!extracted.isEmpty()) {
                    log.info("Extracted JSON from reasoningContent for question={}: {}", event.questionUuid(), extracted);
                    rawJson = extracted;
                }
            }

            // Fallback 2: when finish_reason=length and no JSON is produced, recover structured result
            if (rawJson.isEmpty() && reasoningContent != null && !reasoningContent.isBlank()) {
                String recovered = buildFallbackJsonFromReasoning(reasoningContent);
                if (!recovered.isEmpty()) {
                    log.warn("Recovered AI analysis JSON from reasoning text for question={}, finishReason={}",
                            event.questionUuid(), finishReason);
                    rawJson = recovered;
                }
            }

            // 保留原始响应用于审计落库
            if (rawJson.length() > 0) {
                rawResponseForAudit = rawJson;
            } else if (rawContentStr != null && !rawContentStr.isBlank()) {
                rawResponseForAudit = rawContentStr;
            } else {
                rawResponseForAudit = reasoningContent;
            }

            log.info("AI analysis raw response for question={}: {}...(len={})",
                    event.questionUuid(),
                    rawJson.length() > 200 ? rawJson.substring(0, 200) : rawJson,
                    rawJson.length());

            // Validate rawJson is parseable JSON; if not (e.g. model returned LaTeX fragment),
            // try to extract an embedded JSON object, or clear so Fallback 2 can fire.
            if (!rawJson.isEmpty()) {
                try {
                    objectMapper.readTree(rawJson);
                    // Valid JSON — proceed
                } catch (Exception preCheckEx) {
                    log.warn("rawJson is not valid JSON for question={}, preview='{}'; clearing for reasoning fallback",
                            event.questionUuid(),
                            rawJson.length() > 80 ? rawJson.substring(0, 80) : rawJson);
                    // rawJson itself is the invalid content — extracting from it yields the same garbage.
                    // Try rawContentStr only if it differs and is non-empty.
                    String embedded = "";
                    if (rawContentStr != null && !rawContentStr.isBlank() && !rawContentStr.trim().equals(rawJson)) {
                        embedded = extractJsonFromText(rawContentStr);
                    }
                    if (!embedded.isEmpty()) {
                        log.info("Salvaged embedded JSON from rawContentStr for question={}", event.questionUuid());
                        rawJson = embedded;
                    } else {
                        // Give up; clear so buildFallbackJsonFromReasoning can run
                        log.warn("No valid JSON salvageable for question={}, clearing for fallback", event.questionUuid());
                        rawJson = "";
                    }
                }
            }

            // Fallback 3 (re-run): if rawJson was cleared by validation above, try reasoning again
            if (rawJson.isEmpty() && reasoningContent != null && !reasoningContent.isBlank()) {
                String recovered = buildFallbackJsonFromReasoning(reasoningContent);
                if (!recovered.isEmpty()) {
                    log.warn("Fallback 3: built JSON from reasoning after invalid rawJson for question={}", event.questionUuid());
                    rawJson = recovered;
                }
            }

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
                rabbitTemplate.convertAndSend(DbPersistConstants.DB_EXCHANGE, DbPersistConstants.ROUTING_DB_PERSIST,
                        DbWriteBackEvent.aiLocalFailed(event.taskUuid(), event.questionUuid(),
                                "AI model returned empty response (" + hint + ")", userPrompt, rawResponseForAudit));
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

            // 异步落库：SUCCESS
            String tagsJson = null;
            try { tagsJson = objectMapper.writeValueAsString(tags); } catch (Exception ignored) {}
            rabbitTemplate.convertAndSend(DbPersistConstants.DB_EXCHANGE, DbPersistConstants.ROUTING_DB_PERSIST,
                    DbWriteBackEvent.aiLocalSuccess(event.taskUuid(), event.questionUuid(),
                            tagsJson, difficulty, reasoning, userPrompt, rawResponseForAudit));

            publishResult(event, true, tags, difficulty, reasoning, null);

        } catch (Exception ex) {
            log.error("AI analysis failed for question={}", event.questionUuid(), ex);
            String errMsg = ex.getMessage();
            if (errMsg != null && errMsg.length() > 2000) { errMsg = errMsg.substring(0, 2000); }
            rabbitTemplate.convertAndSend(DbPersistConstants.DB_EXCHANGE, DbPersistConstants.ROUTING_DB_PERSIST,
                    DbWriteBackEvent.aiLocalFailed(event.taskUuid(), event.questionUuid(),
                            errMsg, userPrompt, rawResponseForAudit));
            publishResult(event, false, null, null, null, ex.getMessage());
        }
    }

    private String buildUserPrompt(AiAnalysisTaskCreatedEvent event) {
        String rawStem = event.stemXml() == null ? "" : event.stemXml();
        // Strip <image .../> tags so the AI doesn't hallucinate image-based tags
        String cleanedStem = rawStem.replaceAll("<image[^>]*/?>", "");
        String stem = truncate(cleanedStem, MAX_STEM_CHARS);
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
                event.taskUuid(),
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

    /**
     * Extracts JSON between ##RESULT_START## and ##RESULT_END## delimiters.
     * This is the primary extraction method — delimiter-based extraction is immune
     * to LaTeX braces, Markdown, and other noise in the model output.
     */
    private String extractByDelimiter(String text) {
        if (text == null) return "";
        int s = text.indexOf(RESULT_START);
        int e = text.indexOf(RESULT_END);
        if (s < 0 || e < 0 || e <= s) return "";
        return text.substring(s + RESULT_START.length(), e).trim();
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
     * Handles cases where the AI model returns double-escaped JSON:
     * Case 1: outer JSON string wrapping e.g. "{\"tags\":[...]}" (starts and ends with quote)
     * Case 2: literal backslash-escaped JSON e.g. {\"tags\":[...]} (starts with {\ )
     * Both are caused by the model serialising its output as a JSON string instead of raw JSON.
     */
    private String unescapeDoubleEncodedJson(String text) {
        if (text == null || text.isEmpty()) return text;
        // Case 1: outer JSON string wrapping - try Jackson string unwrap first
        if (text.startsWith("\"") && text.endsWith("\"") && text.length() > 2) {
            try {
                String inner = objectMapper.readValue(text, String.class).trim();
                if (inner.startsWith("{")) {
                    log.info("Unwrapped outer-quoted AI JSON string (len={})", text.length());
                    return inner;
                }
            } catch (Exception ignored) {}
        }
        // Case 2: starts with {\" - literal backslash escaping throughout
        if (text.length() >= 2 && text.charAt(0) == '{' && text.charAt(1) == '\\') {
            String recovered = text
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t");
            try {
                objectMapper.readTree(recovered);
                log.info("Unescaped double-encoded AI JSON (original len={})", text.length());
                return recovered;
            } catch (Exception ignored) {}
        }
        return text;
    }

    /**
     * Scans text for the first balanced JSON object { ... } that is also valid JSON,
     * retrying from the next '{' occurrence if validation fails.
     * LaTeX expressions like {a_{n}\} or {\circ} are skipped automatically.
     */
    private String extractJsonFromText(String text) {
        int searchFrom = 0;
        while (searchFrom < text.length()) {
            int start = text.indexOf('{', searchFrom);
            if (start < 0) return "";
            int depth = 0;
            int end = -1;
            for (int i = start; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) {
                        end = i;
                        break;
                    }
                }
            }
            if (end < 0) return ""; // no closing brace found at all
            String candidate = text.substring(start, end + 1);
            try {
                objectMapper.readTree(candidate);
                return candidate; // valid JSON — done
            } catch (Exception ignored) {
                searchFrom = start + 1; // this candidate is LaTeX/garbage, try next '{'
            }
        }
        return "";
    }

    private String buildFallbackJsonFromReasoning(String reasoningContent) {
        List<String> tags = extractTagsFromReasoning(reasoningContent);
        BigDecimal difficulty = extractDifficultyFromReasoning(reasoningContent);

        if (tags.isEmpty()) {
            tags = List.of("立体几何", "空间向量");
        }
        if (difficulty == null) {
            difficulty = new BigDecimal("0.50");
        }

        String compactReasoning = reasoningContent.replaceAll("\\s+", " ").trim();
        if (compactReasoning.length() > 80) {
            compactReasoning = compactReasoning.substring(0, 80);
        }
        if (compactReasoning.isBlank()) {
            compactReasoning = "基于题干与答案语义进行标签和难度估计";
        }

        try {
            JsonNode root = objectMapper.createObjectNode()
                    .putPOJO("tags", tags)
                    .put("difficulty", difficulty.setScale(2, RoundingMode.HALF_UP))
                    .put("reasoning", compactReasoning);
            return objectMapper.writeValueAsString(root);
        } catch (Exception ex) {
            log.error("Failed to build fallback JSON from reasoning", ex);
            return "";
        }
    }

    private List<String> extractTagsFromReasoning(String text) {
        Set<String> tags = new LinkedHashSet<>();

        Matcher matcher = QUOTED_TAG_PATTERN.matcher(text);
        while (matcher.find() && tags.size() < 5) {
            String candidate = matcher.group(1).trim();
            if (isValidTagCandidate(candidate)) {
                tags.add(candidate);
            }
        }

        String[] keywordTags = {
                "空间向量", "直线与平面平行", "二面角", "直线与平面所成角", "立体几何", "最值问题"
        };
        for (String keyword : keywordTags) {
            if (text.contains(keyword) && tags.size() < 5) {
                tags.add(keyword);
            }
        }

        return new ArrayList<>(tags);
    }

    private boolean isValidTagCandidate(String candidate) {
        if (candidate.isBlank() || candidate.length() > 20) {
            return false;
        }
        return !(candidate.contains("标签")
                || candidate.contains("任务")
                || candidate.contains("difficulty")
                || candidate.contains("reasoning")
                || candidate.contains("JSON"));
    }

    private BigDecimal extractDifficultyFromReasoning(String text) {
        Matcher matcher = DIFFICULTY_PATTERN.matcher(text);
        if (matcher.find()) {
            try {
                BigDecimal value = new BigDecimal(matcher.group(1)).setScale(2, RoundingMode.HALF_UP);
                if (value.compareTo(BigDecimal.ZERO) < 0) {
                    return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                if (value.compareTo(BigDecimal.ONE) > 0) {
                    return BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
                }
                return value;
            } catch (Exception ignored) {
                // fall through
            }
        }
        Matcher fallbackMatcher = DIFFICULTY_PATTERN_FALLBACK.matcher(text);
        if (fallbackMatcher.find()) {
            try {
                BigDecimal value = new BigDecimal(fallbackMatcher.group(1)).setScale(2, RoundingMode.HALF_UP);
                if (value.compareTo(BigDecimal.ZERO) < 0) {
                    return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                }
                if (value.compareTo(BigDecimal.ONE) > 0) {
                    return BigDecimal.ONE.setScale(2, RoundingMode.HALF_UP);
                }
                return value;
            } catch (Exception ignored) {
                // fall through
            }
        }
        return null;
    }
}
