package io.github.kamill7779.qforge.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * ZhipuAI SDK 配置属性
 * 对应 application.yml 中 zhipuai.* 前缀
 */
@Data
@ConfigurationProperties(prefix = "zhipuai")
public class ZhipuProperties {

    /** API 密钥（格式：id.secret，官方 SDK 自动签名 JWT） */
    private String apiKey;

    /** 模型名称，如 glm-5 / glm-4-flash 等 */
    private String model = "glm-5";

    /** 生成温度（0-1），值越大结果越随机 */
    private Float temperature = 0.7f;

    /** 最大生成 token 数 */
    private Integer maxTokens = 1024;

    /** 请求超时（毫秒） */
    private Integer timeout = 30000;
}
