package io.github.kamill7779.qforge.question.dto;

import java.util.List;

public record MainTagCategoryResponse(
        String categoryCode,
        String categoryName,
        List<TagOptionResponse> options
) {
}

