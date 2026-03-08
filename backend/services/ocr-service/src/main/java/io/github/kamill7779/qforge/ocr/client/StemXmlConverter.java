package io.github.kamill7779.qforge.ocr.client;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import ai.z.openapi.service.model.ChatMessage;
import ai.z.openapi.service.model.ChatMessageRole;
import io.github.kamill7779.qforge.ocr.config.QForgeOcrProperties;
import io.github.kamill7779.qforge.ocr.config.StemXmlProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * OCR 后处理器：调用 GLM 将 OCR 纯文本转换为 stem XML 格式。
 * <p>
 * 流程：OCR (layout_parsing) → 纯文本 → GLM (glm-4-0520) → {@code <stem version="1">...}
 * <p>
 * 使用独立的 {@link StemXmlProperties} 配置（{@code stemxml.*}），
 * 与 AI 分析（{@code zhipuai.*} / GLM-5）分离，避免深度推理模型的
 * token 耗尽导致 content 为空的问题。
 */
@Component
public class StemXmlConverter {

    private static final Logger log = LoggerFactory.getLogger(StemXmlConverter.class);

    private static final String SYSTEM_PROMPT = String.join("\n",
            "You are an XML conversion engine. Convert OCR-recognized math problem text into well-formed XML.",
            "",
            "SCHEMA:",
            "Root: <stem version=\"1\">",
            "Allowed tags (ONLY these 14): stem, p, image, choices, choice, blanks, blank, answer-area, table, thead, tbody, tr, th, td",
            "",
            "RULES:",
            "1. <stem version=\"1\"> is the root.",
            "2. <p> wraps text paragraphs. Inline LaTeX uses $...$.",
            "3. <choices mode=\"single\"> for single-choice (or mode=\"multi\" for multi-select) must have >= 2 <choice>.",
            "4. <choice key=\"A\"> wraps content in <p>.",
            "5. Fill-in-the-blank: <blank id=\"N\" /> inline in <p>.",
            "6. Essay/proof: <answer-area /> at the end.",
            "7. <image /> tags: The input may contain <image ref=\"fig-N\" bbox=\"...\" /> placeholders.",
            "   In the output, keep each <image ref=\"fig-N\" /> tag at the correct position ",
            "   but REMOVE the bbox attribute. Multiple images are allowed. If no image tags appear in the input, do not add any.",
            "8. Math formulas: standard LaTeX (\\frac{}{}, \\sqrt{}, \\vec{}, etc.).",
            "9. Escape XML special chars: &lt; &gt; &amp;",
            "10. Data tables: use <table> with optional <thead>/<tbody>, <tr>, <th>/<td>.",
            "    Each cell can contain inline LaTeX. Use this for tabular data in problems.",
            "",
            "QUESTION TYPE DETECTION:",
            "- A/B/C/D options → <choices mode=\"single\">",
            "- 多选/multiple-select → <choices mode=\"multi\">",
            "- Blanks to fill → <blank id=\"N\" />",
            "- Proof/detailed solution → <answer-area />",
            "",
            "OUTPUT RULES:",
            "- Output ONLY the XML. No explanations, no markdown fences, no code blocks.",
            "- Preserve all mathematical content exactly.",
            "- Remove question numbers (e.g. '11.', '(1)') from the beginning.",
            "",
            "EXAMPLES:",
            "",
            "Input: 下列哪个是勾股定理的正确表述？ A. $a^2 + b^2 = c^2$ B. $a + b = c$ C. $a^2 - b^2 = c^2$ D. $a^2 + b^2 = c$",
            "Output:",
            "<stem version=\"1\">",
            "  <p>下列哪个是勾股定理的正确表述？</p>",
            "  <choices mode=\"single\">",
            "    <choice key=\"A\"><p>$a^2 + b^2 = c^2$</p></choice>",
            "    <choice key=\"B\"><p>$a + b = c$</p></choice>",
            "    <choice key=\"C\"><p>$a^2 - b^2 = c^2$</p></choice>",
            "    <choice key=\"D\"><p>$a^2 + b^2 = c$</p></choice>",
            "  </choices>",
            "</stem>",
            "",
            "Input: 已知 $f(x) = x^2$，则 $f(3) = $ ____。若 $f(a) = 16$，则 $a = $ ____。",
            "Output:",
            "<stem version=\"1\">",
            "  <p>已知 $f(x) = x^2$，则 $f(3) = $ <blank id=\"1\" />。</p>",
            "  <p>若 $f(a) = 16$，则 $a = $ <blank id=\"2\" />。</p>",
            "</stem>",
            "",
            "Input: 已知直角三角形两直角边长分别为 3 和 4，求斜边长度。要求写出完整解题过程。",
            "Output:",
            "<stem version=\"1\">",
            "  <p>已知直角三角形两直角边长分别为 3 和 4，求斜边长度。要求写出完整解题过程。</p>",
            "  <answer-area />",
            "</stem>",
            "",
            "Input: 如图所示，在三角形 ABC 中，已知 $AB = 5$，$BC = 7$。",
            "<image ref=\"fig-1\" bbox=\"100,200,400,500\" />",
            "求角 A 的大小。",
            "Output:",
            "<stem version=\"1\">",
            "  <p>如图所示，在三角形 ABC 中，已知 $AB = 5$，$BC = 7$。</p>",
            "  <image ref=\"fig-1\" />",
            "  <p>求角 A 的大小。</p>",
            "  <answer-area />",
            "</stem>"
    );

    private final ZhipuAiClient zhipuAiClient;
    private final StemXmlProperties properties;
    private final QForgeOcrProperties ocrProps;

    public StemXmlConverter(ZhipuAiClient zhipuAiClient, StemXmlProperties properties,
                             QForgeOcrProperties ocrProps) {
        this.zhipuAiClient = zhipuAiClient;
        this.properties = properties;
        this.ocrProps = ocrProps;
    }

    /**
     * 将 OCR 识别的纯文本转为 stem XML。
     *
     * @param ocrText OCR 输出的纯文本（含 LaTeX）
     * @return stem XML 字符串
     * @throws RuntimeException 如果 GLM 调用失败或返回内容为空
     */
    public String convertToStemXml(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return ocrText;
        }

        int maxRetries = ocrProps.getLlmEmptyRetries();
        log.info("Converting OCR text to stem XML via GLM (model={}, text_len={})",
                properties.getModel(), ocrText.length());

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            String xml = doConvert(ocrText);
            if (xml != null && !xml.isBlank()) {
                log.info("Stem XML conversion complete (xml_len={}, attempt={})", xml.length(), attempt);
                return xml;
            }
            log.warn("GLM returned empty content for stem XML conversion (attempt={}/{})", attempt, maxRetries);
        }

        throw new RuntimeException("GLM stem XML conversion returned empty content after "
                + maxRetries + " attempts (model=" + properties.getModel() + ")");
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
            throw new RuntimeException("GLM stem XML conversion failed: " + response.getMsg());
        }

        Object content = response.getData().getChoices().get(0).getMessage().getContent();
        String xml = content != null ? content.toString().trim() : "";

        // Strip markdown code fences if the model wrapped the output
        xml = stripCodeFences(xml);
        return xml;
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
