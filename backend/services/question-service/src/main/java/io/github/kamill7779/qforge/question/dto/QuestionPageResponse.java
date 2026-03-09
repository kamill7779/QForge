package io.github.kamill7779.qforge.question.dto;

import java.util.List;

public record QuestionPageResponse(
        int page,
        int size,
        long total,
        boolean hasMore,
        List<QuestionOverviewResponse> items
) {
}
