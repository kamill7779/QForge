package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class UpdateSourceRequest {

    @NotBlank(message = "source must not be blank")
    @Size(max = 255)
    private String source;

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
