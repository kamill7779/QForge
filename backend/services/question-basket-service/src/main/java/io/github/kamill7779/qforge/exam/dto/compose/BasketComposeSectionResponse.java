package io.github.kamill7779.qforge.exam.dto.compose;

import java.math.BigDecimal;
import java.util.List;

public record BasketComposeSectionResponse(
        String sectionUuid,
        String title,
        String description,
        String questionTypeCode,
        BigDecimal defaultScore,
        int sortOrder,
        List<BasketComposeQuestionResponse> questions
) {
}
