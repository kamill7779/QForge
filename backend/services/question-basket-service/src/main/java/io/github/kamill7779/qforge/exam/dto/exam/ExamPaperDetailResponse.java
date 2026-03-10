package io.github.kamill7779.qforge.exam.dto.exam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ExamPaperDetailResponse(
        String paperUuid,
        String title,
        String subtitle,
        String description,
        Integer durationMinutes,
        BigDecimal totalScore,
        String status,
        List<ExamSectionResponse> sections,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
