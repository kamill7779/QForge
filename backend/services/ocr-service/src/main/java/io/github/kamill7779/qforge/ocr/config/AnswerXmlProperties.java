package io.github.kamill7779.qforge.ocr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AnswerXml conversion model settings, bound from {@code answerxml.*}.
 * Keep this isolated from stemxml so answer OCR can use an independent model.
 */
@ConfigurationProperties(prefix = "answerxml")
public class AnswerXmlProperties {

    private String model = "glm-4-0520";
    private Float temperature = 0.1f;
    private Integer maxTokens = 4096;

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Float getTemperature() {
        return temperature;
    }

    public void setTemperature(Float temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}

