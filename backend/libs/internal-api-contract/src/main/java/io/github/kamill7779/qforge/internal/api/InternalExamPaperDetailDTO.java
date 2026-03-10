package io.github.kamill7779.qforge.internal.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record InternalExamPaperDetailDTO(
        String paperUuid,
        String title,
        String subtitle,
        String description,
        Integer durationMinutes,
        BigDecimal totalScore,
        String status,
        List<InternalExamSectionDTO> sections,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
