package io.github.kamill7779.qforge.question.dto;

import java.math.BigDecimal;

/**
 * 异步落库写回事件。
 * <p>
 * 业务消费者（{@code AiAnalysisResultConsumer}、{@code OcrResultConsumer}）
 * 完成 Redis 热状态更新 + WebSocket 推送后，将该事件投递到
 * {@code qforge.db.persist.q}，由独立的 {@code DbPersistConsumer} 负责
 * 将最终状态持久化到 MySQL。
 *
 * <p><b>幂等设计：</b>消字段 {@code taskUuid} 天然唯一，消费者收到重复消息时
 * 应先检查 DB 中的现有状态，若已处于终态（APPLIED / CONFIRMED / FAILED）
 * 则跳过本次写入。
 */
public record DbWriteBackEvent(
        /** "AI" 或 "OCR" */
        String taskType,

        String taskUuid,
        String questionUuid,

        /**
         * 最终状态：AI 为 SUCCESS/FAILED，OCR 为 CONFIRMED/FAILED。
         */
        String status,

        /** 发起任务的用户名（requestUser），upsert 时作为兜底字段。 */
        String userId,

        /** OCR 任务的业务类型（QUESTION_STEM / ANSWER_CONTENT），AI 任务填 null。 */
        String bizType,

        // ---- AI 专属字段 ----

        /** 序列化后的 JSON 标签数组，如 {@code ["高中数学","函数"]}。 SUCCESS 时填写，FAILED 时为 null。 */
        String suggestedTagsJson,

        /** AI 推荐难度系数，SUCCESS 时填写，FAILED 时为 null。 */
        BigDecimal suggestedDifficulty,

        /** AI 分析推理过程，SUCCESS 时填写，FAILED 时为 null。已截断至最大列长度。 */
        String reasoning,

        // ---- OCR / 共用字段 ----

        /** OCR 识别文本，SUCCESS 时填写，FAILED 时为 null。 */
        String recognizedText,

        /** 错误信息，FAILED 时填写，SUCCESS 时为 null。已截断至最大列长度。 */
        String errorMsg
) {}
