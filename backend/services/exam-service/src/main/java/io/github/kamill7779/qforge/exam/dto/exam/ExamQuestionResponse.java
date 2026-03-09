package io.github.kamill7779.qforge.exam.dto.exam;

import java.math.BigDecimal;

public record ExamQuestionResponse(
        String questionUuid,
        String stemText,
        BigDecimal score,
        int sortOrder,
        String note
) {
}
