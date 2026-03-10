package io.github.kamill7779.qforge.internal.api;

import java.util.List;

public record BasketComposeCreateExamRequest(
        String title,
        String subtitle,
        String description,
        Integer durationMinutes,
        List<BasketComposeSectionCreateRequest> sections
) {
}
