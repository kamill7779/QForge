package io.github.kamill7779.qforge.internal.api;

import java.math.BigDecimal;

public record BasketComposeQuestionCreateRequest(
        String questionUuid,
        BigDecimal score,
        String note
) {
}
