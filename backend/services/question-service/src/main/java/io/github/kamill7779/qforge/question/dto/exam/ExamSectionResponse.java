package io.github.kamill7779.qforge.question.dto.exam;

import java.math.BigDecimal;
import java.util.List;

/**
 * 大题响应。
 */
public record ExamSectionResponse(
        String sectionUuid,
        String title,
        String description,
        String questionTypeCode,
        BigDecimal defaultScore,
        int sortOrder,
        List<ExamQuestionResponse> questions
) {
}
