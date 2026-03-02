package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class OcrTaskSubmitRequest {

    /**
     * OCR 业务类型：QUESTION_STEM（题干）/ ANSWER_CONTENT（答案）。
     */
    @NotBlank
    @Pattern(regexp = "QUESTION_STEM|ANSWER_CONTENT", message = "bizType must be QUESTION_STEM or ANSWER_CONTENT")
    private String bizType;

    @NotBlank
    private String imageBase64;

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }
}

