package io.github.kamill7779.qforge.question.dto.exam;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 试卷列表概览响应（不含详细题目）。
 */
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
