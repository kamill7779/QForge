package io.github.kamill7779.qforge.ocr.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 逐题调用 StemXmlConverter + AnswerXmlConverter，将拆题结果转为 XML。
 * <p>
 * 单题转换失败时降级为简单 XML 包裹，不中断整个流程。
 */
@Component
public class ExamQuestionXmlGenerator {

    private static final Logger log = LoggerFactory.getLogger(ExamQuestionXmlGenerator.class);

    private final StemXmlConverter stemXmlConverter;
    private final AnswerXmlConverter answerXmlConverter;

    public ExamQuestionXmlGenerator(StemXmlConverter stemXmlConverter,
                                     AnswerXmlConverter answerXmlConverter) {
        this.stemXmlConverter = stemXmlConverter;
        this.answerXmlConverter = answerXmlConverter;
    }

    /**
     * XML 转换结果。
     */
    public record XmlResult(
            String stemXml,
            String answerXml,
            boolean stemError,
            boolean answerError,
            String errorMsg
    ) {
    }

    /**
     * 将一道题的原始文本转为 XML。
     *
     * @param seqNo         题目序号
     * @param rawStemText   原始题干文本（ref 已替换为最终格式）
     * @param rawAnswerText 原始答案文本（可为空）
     * @return XML 转换结果
     */
    public XmlResult generate(int seqNo, String rawStemText, String rawAnswerText) {
        String stemXml = null;
        String answerXml = null;
        boolean stemError = false;
        boolean answerError = false;
        StringBuilder errorMsg = new StringBuilder();

        // Stem XML
        try {
            if (rawStemText != null && !rawStemText.isBlank()) {
                stemXml = stemXmlConverter.convertToStemXml(rawStemText);
            }
        } catch (Exception ex) {
            log.warn("StemXml conversion failed for seq={}: {}", seqNo, ex.getMessage());
            stemError = true;
            errorMsg.append("StemXml error: ").append(ex.getMessage());
            // 降级：简单包裹
            stemXml = "<stem version=\"1\"><p>" + escapeXml(rawStemText) + "</p></stem>";
        }

        // Answer XML
        try {
            if (rawAnswerText != null && !rawAnswerText.isBlank()) {
                answerXml = answerXmlConverter.convertToAnswerXml(rawAnswerText);
            }
        } catch (Exception ex) {
            log.warn("AnswerXml conversion failed for seq={}: {}", seqNo, ex.getMessage());
            answerError = true;
            if (!errorMsg.isEmpty()) errorMsg.append("; ");
            errorMsg.append("AnswerXml error: ").append(ex.getMessage());
            // 降级：简单包裹
            answerXml = "<answer version=\"1\"><p>" + escapeXml(rawAnswerText) + "</p></answer>";
        }

        return new XmlResult(stemXml, answerXml, stemError, answerError,
                errorMsg.isEmpty() ? null : errorMsg.toString());
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
