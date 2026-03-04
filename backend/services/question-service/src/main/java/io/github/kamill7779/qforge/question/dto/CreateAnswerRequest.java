package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public class CreateAnswerRequest {

    @NotBlank
    private String latexText;

    /**
     * 答案内联图片，key 为 XML 中的 ref（如 img-1），value 为 base64 + mimeType。
     * 可选；为 null 时不处理图片。
     */
    private Map<String, InlineImageEntry> inlineImages;

    public String getLatexText() {
        return latexText;
    }

    public void setLatexText(String latexText) {
        this.latexText = latexText;
    }

    public Map<String, InlineImageEntry> getInlineImages() {
        return inlineImages;
    }

    public void setInlineImages(Map<String, InlineImageEntry> inlineImages) {
        this.inlineImages = inlineImages;
    }
}

