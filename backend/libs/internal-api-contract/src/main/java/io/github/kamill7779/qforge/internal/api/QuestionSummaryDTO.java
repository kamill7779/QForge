package io.github.kamill7779.qforge.internal.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 题目摘要 — 只含展示必需字段，供 exam-service 试卷详情/试题篮列表使用。
 */
public record QuestionSummaryDTO(
        Long questionId,
        String questionUuid,
        String status,
        String stemText,
        BigDecimal difficulty,
        String source,
        long answerCount
) {}
