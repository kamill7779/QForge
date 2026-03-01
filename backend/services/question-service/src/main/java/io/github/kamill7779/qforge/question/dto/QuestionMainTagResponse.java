package io.github.kamill7779.qforge.question.dto;

public record QuestionMainTagResponse(
        String categoryCode,
        String categoryName,
        String tagCode,
        String tagName
) {
}

