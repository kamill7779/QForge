package io.github.kamill7779.qforge.ocr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ZhipuAI GLM 聊天模型配置属性（用于 OCR 后处理）。
 * <p>
 * 对应 application.yml 中 {@code zhipuai.*} 前缀。
 */
@ConfigurationProperties(prefix = "zhipuai")
public class ZhipuAiProperties {

    /** API 密钥（格式：id.secret，SDK 内部自动签名 JWT） */
    private String apiKey;

    /** 模型名称，如 glm-5 / glm-4-flash */
    private String model = "glm-5";

    /** 生成温度（0-1） */
    private Float temperature = 0.1f;

    /** 最大生成 token 数 */
    private Integer maxTokens = 2048;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

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
