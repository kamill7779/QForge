package io.github.kamill7779.qforge.question.dto;

import java.util.List;

public record TagCatalogResponse(
        List<MainTagCategoryResponse> mainCategories,
        String secondaryCategoryCode,
        String secondaryCategoryName
) {
}
