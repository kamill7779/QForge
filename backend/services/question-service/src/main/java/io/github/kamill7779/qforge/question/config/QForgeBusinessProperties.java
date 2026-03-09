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

    // ── 内联图片限制 ──

    /** 每道题最多内联图片张数（默认 10）。 */
    private int maxInlineImages = 10;

    /** 每张内联图片二进制字节上限（默认 512 KB = 524288 B）。 */
    private int maxImageBinaryBytes = 524_288;

    // ── AI 文本截断 ──

    /** AI 推理文本存入 DB 前的截断长度（默认 1024）。 */
    private int maxReasoningLength = 1024;

    /** AI 错误信息存入 DB 前的截断长度（默认 2048）。 */
    private int maxErrorMessageLength = 2048;

    // ── Redis TTL（热状态缓存） ──

    /** AI/OCR 任务热状态 TTL（分钟），默认 30。 */
    private int taskStateTtlMinutes = 30;

    /** 答案 OCR 防重 TTL（分钟），默认 10。 */
    private int answerOcrGuardTtlMinutes = 10;

    /** 答案 OCR 资产缓存 TTL（小时），默认 6。 */
    private int answerOcrAssetTtlHours = 6;

    /** OCR 结果图片缓存 TTL（秒），默认 30。 */
    private int assetCacheTtlSeconds = 30;

    // ── WebSocket ──

    /** WebSocket 允许的 Origin 模式，默认 "*"（生产环境应限制）。 */
    private String wsAllowedOrigins = "*";

    // ── Getters & Setters ──

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

    public int getTaskStateTtlMinutes() {
        return taskStateTtlMinutes;
    }

    public void setTaskStateTtlMinutes(int taskStateTtlMinutes) {
        this.taskStateTtlMinutes = taskStateTtlMinutes;
    }

    public int getAnswerOcrGuardTtlMinutes() {
        return answerOcrGuardTtlMinutes;
    }

    public void setAnswerOcrGuardTtlMinutes(int answerOcrGuardTtlMinutes) {
        this.answerOcrGuardTtlMinutes = answerOcrGuardTtlMinutes;
    }

    public int getAnswerOcrAssetTtlHours() {
        return answerOcrAssetTtlHours;
    }

    public void setAnswerOcrAssetTtlHours(int answerOcrAssetTtlHours) {
        this.answerOcrAssetTtlHours = answerOcrAssetTtlHours;
    }

    public int getAssetCacheTtlSeconds() {
        return assetCacheTtlSeconds;
    }

    public void setAssetCacheTtlSeconds(int assetCacheTtlSeconds) {
        this.assetCacheTtlSeconds = assetCacheTtlSeconds;
    }

    public String getWsAllowedOrigins() {
        return wsAllowedOrigins;
    }

    public void setWsAllowedOrigins(String wsAllowedOrigins) {
        this.wsAllowedOrigins = wsAllowedOrigins;
    }
}
