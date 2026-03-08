package io.github.kamill7779.qforge.question.dto.exam;

import java.math.BigDecimal;

/**
 * 试卷内单道题响应。
 */
public record ExamQuestionResponse(
        String questionUuid,
        String stemText,
        BigDecimal score,
        int sortOrder,
        String note
) {
}
