package io.github.kamill7779.qforge.common.contract;

import java.math.BigDecimal;

/**
 * 异步落库事件 —— 由业务消费者投递到 {@value DbPersistConstants#DB_PERSIST_QUEUE}，
 * 由 persist-service 的 {@code DbPersistConsumer} 统一写入 MySQL。
 *
 * <h3>taskType 取值</h3>
 * <table>
 *   <tr><td>{@code AI}</td><td>question-service → q_question_ai_task</td></tr>
 *   <tr><td>{@code OCR}</td><td>question-service → q_question_ocr_task</td></tr>
 *   <tr><td>{@code OCR_LOCAL}</td><td>ocr-service → q_ocr_task</td></tr>
 *   <tr><td>{@code AI_LOCAL}</td><td>ocr-service → q_ai_analysis_task</td></tr>
 * </table>
 *
 * <p>事件携带全量字段，消费端无需 Redis 依赖（TTL 安全）。</p>
 */
public record DbWriteBackEvent(
        /** "AI" / "OCR" / "OCR_LOCAL" / "AI_LOCAL" */
        String taskType,
        String taskUuid,
        String questionUuid,
        /** SUCCESS / FAILED / PROCESSING / CONFIRMED 等 */
        String status,
        /** requestUser（可为 null） */
        String userId,
        /** OCR / OCR_LOCAL 专用 (QUESTION_STEM / ANSWER_CONTENT) */
        String bizType,
        /** AI / AI_LOCAL SUCCESS */
        String suggestedTagsJson,
        /** AI / AI_LOCAL SUCCESS */
        BigDecimal suggestedDifficulty,
        /** AI / AI_LOCAL SUCCESS */
        String reasoning,
        /** OCR / OCR_LOCAL SUCCESS */
        String recognizedText,
        /** FAILED 分支 */
        String errorMsg,
        // ---- OCR_LOCAL / AI_LOCAL 扩展字段 ----
        /** OCR_LOCAL: provider 名称 (e.g. "GLM_OCR") */
        String provider,
        /** AI_LOCAL: 模型名称 (e.g. "glm-5") */
        String model,
        /** AI_LOCAL: 用户 prompt（审计） */
        String userPrompt,
        /** AI_LOCAL: 模型原始响应（审计） */
        String rawResponse
) {

    // ============ 便捷工厂方法 ============

    /** question-service AI 结果写回。 */
    public static DbWriteBackEvent ai(String taskUuid, String questionUuid, String status,
                                       String userId, String suggestedTagsJson,
                                       BigDecimal suggestedDifficulty, String reasoning,
                                       String errorMsg) {
        return new DbWriteBackEvent("AI", taskUuid, questionUuid, status,
                userId, null, suggestedTagsJson, suggestedDifficulty, reasoning,
                null, errorMsg, null, null, null, null);
    }

    /** question-service OCR 结果写回。 */
    public static DbWriteBackEvent ocr(String taskUuid, String questionUuid, String status,
                                        String userId, String bizType,
                                        String recognizedText, String errorMsg) {
        return new DbWriteBackEvent("OCR", taskUuid, questionUuid, status,
                userId, bizType, null, null, null,
                recognizedText, errorMsg, null, null, null, null);
    }

    /** ocr-service OCR 本地任务状态写回。 */
    public static DbWriteBackEvent ocrLocal(String taskUuid, String status,
                                             String recognizedText, String errorMsg) {
        return new DbWriteBackEvent("OCR_LOCAL", taskUuid, null, status,
                null, null, null, null, null,
                recognizedText, errorMsg, null, null, null, null);
    }

    /** ocr-service AI 分析本地任务 —— PROCESSING 初始化。 */
    public static DbWriteBackEvent aiLocalProcessing(String taskUuid, String questionUuid,
                                                      String userId, String model,
                                                      String userPrompt) {
        return new DbWriteBackEvent("AI_LOCAL", taskUuid, questionUuid, "PROCESSING",
                userId, null, null, null, null,
                null, null, null, model, userPrompt, null);
    }

    /** ocr-service AI 分析本地任务 —— SUCCESS。 */
    public static DbWriteBackEvent aiLocalSuccess(String taskUuid, String questionUuid,
                                                   String suggestedTagsJson,
                                                   BigDecimal suggestedDifficulty,
                                                   String reasoning,
                                                   String userPrompt,
                                                   String rawResponse) {
        return new DbWriteBackEvent("AI_LOCAL", taskUuid, questionUuid, "SUCCESS",
                null, null, suggestedTagsJson, suggestedDifficulty, reasoning,
                null, null, null, null, userPrompt, rawResponse);
    }

    /** ocr-service AI 分析本地任务 —— FAILED。 */
    public static DbWriteBackEvent aiLocalFailed(String taskUuid, String questionUuid,
                                                  String errorMsg,
                                                  String userPrompt,
                                                  String rawResponse) {
        return new DbWriteBackEvent("AI_LOCAL", taskUuid, questionUuid, "FAILED",
                null, null, null, null, null,
                null, errorMsg, null, null, userPrompt, rawResponse);
    }
}
