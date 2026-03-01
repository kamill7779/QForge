package io.github.kamill7779.qforge.question.dto;

public record AnswerOverviewResponse(
        String answerUuid,
        String answerType,
        String latexText,
        int sortOrder,
        boolean official
) {
}
