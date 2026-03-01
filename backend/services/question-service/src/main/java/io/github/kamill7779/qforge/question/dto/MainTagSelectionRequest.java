package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotBlank;

public class MainTagSelectionRequest {

    @NotBlank
    private String categoryCode;

    @NotBlank
    private String tagCode;

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getTagCode() {
        return tagCode;
    }

    public void setTagCode(String tagCode) {
        this.tagCode = tagCode;
    }
}

