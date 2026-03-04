package io.github.kamill7779.qforge.question.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 业务参数热配置。
 *
 * <p>对应 Nacos dataId {@code question-service.yml} 中的 {@code qforge.business.*} 段。
 * Nacos 推送配置变更时，Spring Cloud 的 {@code ConfigurationPropertiesRebinder}
 * 会自动重绑此 Bean，所有调用方在下次方法调用时即获取最新值，无需重启服务。
 */
@Component
@ConfigurationProperties(prefix = "qforge.business")
public class QForgeBusinessProperties {

    /** 每道题最多内联图片张数（默认 10）。 */
    private int maxInlineImages = 10;

    /** 每张内联图片二进制字节上限（默认 50 KB = 51200 B）。 */
    private int maxImageBinaryBytes = 51_200;

    /** AI 推理文本存入 DB 前的截断长度（默认 1024）。 */
    private int maxReasoningLength = 1024;

    /** AI 错误信息存入 DB 前的截断长度（默认 2048）。 */
    private int maxErrorMessageLength = 2048;

    public int getMaxInlineImages() {
        return maxInlineImages;
    }

    public void setMaxInlineImages(int maxInlineImages) {
        this.maxInlineImages = maxInlineImages;
    }

    public int getMaxImageBinaryBytes() {
        return maxImageBinaryBytes;
    }

    public void setMaxImageBinaryBytes(int maxImageBinaryBytes) {
        this.maxImageBinaryBytes = maxImageBinaryBytes;
    }

    public int getMaxReasoningLength() {
        return maxReasoningLength;
    }

    public void setMaxReasoningLength(int maxReasoningLength) {
        this.maxReasoningLength = maxReasoningLength;
    }

    public int getMaxErrorMessageLength() {
        return maxErrorMessageLength;
    }

    public void setMaxErrorMessageLength(int maxErrorMessageLength) {
        this.maxErrorMessageLength = maxErrorMessageLength;
    }
}
