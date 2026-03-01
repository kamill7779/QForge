package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotBlank;

public class CreateAnswerRequest {

    @NotBlank
    private String latexText;

    public String getLatexText() {
        return latexText;
    }

    public void setLatexText(String latexText) {
        this.latexText = latexText;
    }
}

