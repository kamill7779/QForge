package io.github.kamill7779.qforge.exam.dto.exam;

import java.math.BigDecimal;
import java.util.List;

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
