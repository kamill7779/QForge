package io.github.kamill7779.qforge.question.dto.exam;

/**
 * 题型响应。
 */
public record QuestionTypeResponse(
        Long id,
        String typeCode,
        String typeLabel,
        String ownerUser,
        String xmlHint,
        int sortOrder,
        boolean enabled,
        boolean system
) {
}
