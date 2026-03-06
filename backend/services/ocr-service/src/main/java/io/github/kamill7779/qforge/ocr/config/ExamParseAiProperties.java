package io.github.kamill7779.qforge.ocr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 试卷自动解析 LLM 配置（GLM-Z-Plus，128K context）。
 * <p>
 * 对应 application.yml 中 {@code examparse.ai.*} 前缀。
 */
@ConfigurationProperties(prefix = "examparse.ai")
public class ExamParseAiProperties {

    /** 模型名称，默认 glm-z-plus */
    private String model = "glm-4-plus";

    /** 生成温度（0-1） */
    private Float temperature = 0.1f;

    /** 最大生成 token 数 */
    private Integer maxTokens = 32768;

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
