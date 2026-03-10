package io.github.kamill7779.qforge.exam.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BasketItemResponse(
        String questionUuid,
        String stemText,
        String source,
        BigDecimal difficulty,
        LocalDateTime addedAt
) {
}
