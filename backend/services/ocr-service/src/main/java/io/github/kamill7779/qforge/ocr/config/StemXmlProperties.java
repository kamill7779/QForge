package io.github.kamill7779.qforge.ocr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * StemXml 转换专用模型配置（非深度推理模型，如 glm-4-0520）。
 * <p>
 * 对应 application.yml 中 {@code stemxml.*} 前缀。
 * 与 {@link ZhipuAiProperties} 分离，使 AI 分析保留 GLM-5 深度推理，
 * 而 StemXml 格式转换使用更快、更稳定的非推理模型。
 */
@ConfigurationProperties(prefix = "stemxml")
public class StemXmlProperties {

    /** 模型名称，默认 glm-4-0520（快速、无推理、输出稳定） */
    private String model = "glm-4-0520";

    /** 生成温度（0-1） */
    private Float temperature = 0.1f;

    /** 最大生成 token 数 */
    private Integer maxTokens = 65536;

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
