package io.github.kamill7779.qforge.exam.dto.exam;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExamPaperOverviewResponse(
        String paperUuid,
        String title,
        String subtitle,
        String status,
        Integer durationMinutes,
        BigDecimal totalScore,
        int sectionCount,
        int questionCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
