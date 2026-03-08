package io.github.kamill7779.qforge.question.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 试题篮条目响应 — 包含题目基本信息用于前端显示。
 */
public record BasketItemResponse(
        String questionUuid,
        String stemText,
        String source,
        BigDecimal difficulty,
        LocalDateTime addedAt
) {
}
