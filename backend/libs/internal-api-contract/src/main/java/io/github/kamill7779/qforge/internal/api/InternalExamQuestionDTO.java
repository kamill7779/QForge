package io.github.kamill7779.qforge.internal.api;

import java.math.BigDecimal;

public record InternalExamQuestionDTO(
        String questionUuid,
        String stemText,
        BigDecimal score,
        int sortOrder,
        String note
) {
}
