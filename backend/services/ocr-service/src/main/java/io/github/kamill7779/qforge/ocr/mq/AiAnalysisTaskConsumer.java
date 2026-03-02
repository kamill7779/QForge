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
                    .temperature(0.7f)
                    .maxTokens(1024)
                    .stream(false)
                    .build();

            ChatCompletionResponse response = zhipuAiClient.chat().createChatCompletion(params);

            if (!response.isSuccess()) {
                publishResult(event, false, null, null, null, "GLM API call failed: " + response.getMsg());
                return;
            }

            Object content = response.getData().getChoices().get(0).getMessage().getContent();
            String rawJson = content != null ? content.toString().trim() : "";
            rawJson = stripCodeFences(rawJson);

            log.info("AI analysis raw response for question={}: {}", event.questionUuid(), rawJson);

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
        StringBuilder sb = new StringBuilder();
        sb.append("## 题目内容\n\n");
        sb.append("### 题干\n");
        sb.append(event.stemXml()).append("\n\n");

        if (event.answerTexts() != null && !event.answerTexts().isEmpty()) {
            sb.append("### 答案\n");
            for (int i = 0; i < event.answerTexts().size(); i++) {
                sb.append("答案 ").append(i + 1).append(": ").append(event.answerTexts().get(i)).append("\n");
            }
        }

        return sb.toString();
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
}
