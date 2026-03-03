package io.github.kamill7779.qforge.question.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record AiTaskResponse(
        String taskUuid,
        String questionUuid,
        String status,
        List<String> suggestedTags,
        BigDecimal suggestedDifficulty,
        String reasoning,
        String errorMessage,
        LocalDateTime appliedAt,
        LocalDateTime createdAt
) {
}
