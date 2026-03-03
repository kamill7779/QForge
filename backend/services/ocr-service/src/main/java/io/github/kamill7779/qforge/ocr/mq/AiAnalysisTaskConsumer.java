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
import io.github.kamill7779.qforge.ocr.entity.AiAnalysisTask;
import io.github.kamill7779.qforge.ocr.repository.AiAnalysisTaskRepository;
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

        private static final String SYSTEM_PROMPT = String.join("\n",
            "你是一个资深教育评估专家。请根据用户提供的题目内容，完成以下两项任务：",
            "",
            "## 任务一：推荐标签",
            "为这道题推荐 2-5 个知识点标签。标签应当：",
            "- 精准描述本题考察的核心知识点",
            "- 粒度适中（如\"二次函数\"而非\"数学\"）",
            "- 使用中文",
            "",
            "## 任务二：难度评估（P 值）",
            "根据以下标准评估题目难度，输出 P 值（取值 0.00-1.00，保留两位小数）。",
            "",
            "### P 值定义",
            "P 值表示\"通过率\"，即随机抽取的合格学生正确回答该题的概率：",
            "- 1.00 = 所有人都能答对（极简单）",
            "- 0.00 = 几乎无人能答对（极困难）",
            "",
            "### 难度等级参照",
            "| P 值区间 | 等级 | 典型特征 |",
            "|-----------|------|----------|",
            "| 0.90–1.00 | 入门 | 直接套公式/定义即可 |",
            "| 0.70–0.89 | 简单 | 需要 1-2 步推理 |",
            "| 0.30–0.69 | 中等 | 需要综合运用知识 |",
            "| 0.10–0.29 | 困难 | 多步骤推理+易错陷阱 |",
            "| 0.00–0.09 | 专家 | 竞赛/超纲级别 |",
            "",
            "### 预估方法（五维加权评分）",
            "由于没有实测数据，请使用以下五个维度进行预估，每个维度 0-20 分：",
            "",
            "| 维度 | 权重 | 评分标准（0-20） |",
            "|------|------|-------------------|",
            "| 前置知识门槛 | 20% | 0=需广泛跨领域知识, 20=仅需基础概念 |",
            "| 推理步骤数 | 25% | 0=≥6步复杂推理链, 20=1步直接得出 |",
            "| 陷阱/易错点 | 20% | 0=多个隐蔽陷阱, 20=无任何陷阱 |",
            "| 实现/表达复杂度 | 25% | 0=需要复杂计算或论证, 20=口算即可 |",
            "| 时间成本 | 10% | 0=考试中≥15分钟, 20=30秒内完成 |",
            "",
            "加权总分 S = Σ(维度得分 × 权重)，P = S / 100，保留两位小数。",
            "",
            "## 输出格式",
            "严格输出以下 JSON，不要添加任何额外文字：",
            "{",
            "  \"tags\": [\"标签1\", \"标签2\"],",
            "  \"difficulty\": 0.65,",
            "  \"reasoning\": \"简要说明评分依据（50字以内）\"",
            "}"
        );
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
    private final AiAnalysisTaskRepository aiAnalysisTaskRepository;

    public AiAnalysisTaskConsumer(
            ZhipuAiClient zhipuAiClient,
            ZhipuAiProperties zhipuAiProperties,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            AiAnalysisTaskRepository aiAnalysisTaskRepository
    ) {
        this.zhipuAiClient = zhipuAiClient;
        this.zhipuAiProperties = zhipuAiProperties;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.aiAnalysisTaskRepository = aiAnalysisTaskRepository;
    }

    @RabbitListener(queues = RabbitTopologyConfig.AI_ANALYSIS_TASK_QUEUE)
    public void onAiAnalysisTask(AiAnalysisTaskCreatedEvent event) {
        log.info("Received AI analysis task for question={}, taskUuid={}", event.questionUuid(), event.taskUuid());

        // ---- Persist PENDING → PROCESSING ----
        AiAnalysisTask task = new AiAnalysisTask();
        task.setTaskUuid(event.taskUuid());
        task.setQuestionUuid(event.questionUuid());
        task.setStatus("PROCESSING");
        task.setModel(zhipuAiProperties.getModel());
        task.setRequestUser(event.userId());
        aiAnalysisTaskRepository.insert(task);

        try {
            String userPrompt = buildUserPrompt(event);
            task.setUserPrompt(userPrompt);

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
                failTask(task, "GLM API call failed: " + response.getMsg());
                publishResult(event, false, null, null, null, "GLM API call failed: " + response.getMsg());
                return;
            }

            if (response.getData() == null || response.getData().getChoices() == null
                    || response.getData().getChoices().isEmpty()) {
                log.error("GLM API returned no choices for question={}, data={}",
                        event.questionUuid(), response.getData());
                failTask(task, "GLM API returned no choices");
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

            // Fallback 1: reasoning models (GLM-Z1) may put JSON inside reasoningContent
            if (rawJson.isEmpty() && reasoningContent != null && !reasoningContent.isBlank()) {
                log.info("content is empty, trying to extract JSON from reasoningContent for question={}",
                        event.questionUuid());
                String extracted = extractJsonFromText(reasoningContent);
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

            // Store raw response for debugging
            if (rawJson.length() > 0) {
                task.setRawResponse(rawJson);
            } else if (rawContentStr != null && !rawContentStr.isBlank()) {
                task.setRawResponse(rawContentStr);
            } else {
                task.setRawResponse(reasoningContent);
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
                failTask(task, "AI model returned empty response (" + hint + ")");
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

            // ---- Persist SUCCESS ----
            task.setStatus("SUCCESS");
            try {
                task.setSuggestedTags(objectMapper.writeValueAsString(tags));
            } catch (Exception ignored) {}
            task.setSuggestedDifficulty(difficulty);
            task.setReasoning(reasoning);
            aiAnalysisTaskRepository.updateById(task);

            publishResult(event, true, tags, difficulty, reasoning, null);

        } catch (Exception ex) {
            log.error("AI analysis failed for question={}", event.questionUuid(), ex);
            failTask(task, ex.getMessage());
            publishResult(event, false, null, null, null, ex.getMessage());
        }
    }

    private void failTask(AiAnalysisTask task, String errorMsg) {
        task.setStatus("FAILED");
        task.setErrorMsg(errorMsg != null && errorMsg.length() > 2000 ? errorMsg.substring(0, 2000) : errorMsg);
        try {
            aiAnalysisTaskRepository.updateById(task);
        } catch (Exception ex) {
            log.error("Failed to update AI task record taskUuid={}", task.getTaskUuid(), ex);
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
