package io.github.kamill7779.qforge.exam.dto.compose;

import java.math.BigDecimal;

public record BasketComposeQuestionResponse(
        String questionUuid,
        String stemText,
        String source,
        BigDecimal difficulty,
        BigDecimal score,
        int sortOrder,
        String note
) {
}
