package io.github.kamill7779.qforge.internal.api;

import java.math.BigDecimal;
import java.util.List;

public record BasketComposeSectionCreateRequest(
        String sectionUuid,
        String title,
        String description,
        String questionTypeCode,
        BigDecimal defaultScore,
        List<BasketComposeQuestionCreateRequest> questions
) {
}
