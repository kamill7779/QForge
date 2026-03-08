package io.github.kamill7779.qforge.ocr.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * OCR 业务参数热配置。
 *
 * <p>对应 Nacos dataId {@code ocr-service.yml} 中的 {@code qforge.ocr.*} 段。
 * Nacos 推送配置变更时，Spring Cloud 的 {@code ConfigurationPropertiesRebinder}
 * 会自动重绑此 Bean，所有调用方在下次方法调用时即获取最新值，无需重启服务。
 */
@Component
@ConfigurationProperties(prefix = "qforge.ocr")
public class QForgeOcrProperties {

    /** PDF 渲染 DPI（影响图像质量和大小），默认 300。 */
    private int pdfRenderDpi = 300;

    /** LLM 返回空内容时的重试次数，默认 2。 */
    private int llmEmptyRetries = 2;

    /** AI 分析任务默认 max tokens，默认 65536。 */
    private int aiDefaultMaxTokens = 65536;

    /** 题干文本截断长度（字符），默认 8000。 */
    private int aiMaxStemChars = 8000;

    /** 单个答案截断长度（字符），默认 2000。 */
    private int aiMaxSingleAnswerChars = 2000;

    /** 最多参考答案条数，默认 6。 */
    private int aiMaxAnswers = 6;

    // ── Getters & Setters ──

    public int getPdfRenderDpi() {
        return pdfRenderDpi;
    }

    public void setPdfRenderDpi(int pdfRenderDpi) {
        this.pdfRenderDpi = pdfRenderDpi;
    }

    public int getLlmEmptyRetries() {
        return llmEmptyRetries;
    }

    public void setLlmEmptyRetries(int llmEmptyRetries) {
        this.llmEmptyRetries = llmEmptyRetries;
    }

    public int getAiDefaultMaxTokens() {
        return aiDefaultMaxTokens;
    }

    public void setAiDefaultMaxTokens(int aiDefaultMaxTokens) {
        this.aiDefaultMaxTokens = aiDefaultMaxTokens;
    }

    public int getAiMaxStemChars() {
        return aiMaxStemChars;
    }

    public void setAiMaxStemChars(int aiMaxStemChars) {
        this.aiMaxStemChars = aiMaxStemChars;
    }

    public int getAiMaxSingleAnswerChars() {
        return aiMaxSingleAnswerChars;
    }

    public void setAiMaxSingleAnswerChars(int aiMaxSingleAnswerChars) {
        this.aiMaxSingleAnswerChars = aiMaxSingleAnswerChars;
    }

    public int getAiMaxAnswers() {
        return aiMaxAnswers;
    }

    public void setAiMaxAnswers(int aiMaxAnswers) {
        this.aiMaxAnswers = aiMaxAnswers;
    }
}
