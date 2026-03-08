package io.github.kamill7779.qforge.question.dto.exam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷完整详情响应（含大题 + 题目）。
 */
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
