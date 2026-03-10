package io.github.kamill7779.qforge.exam.dto.compose;

import java.time.LocalDateTime;
import java.util.List;

public record BasketComposeDetailResponse(
        String composeUuid,
        String title,
        String subtitle,
        String description,
        Integer durationMinutes,
        List<BasketComposeSectionResponse> sections,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
