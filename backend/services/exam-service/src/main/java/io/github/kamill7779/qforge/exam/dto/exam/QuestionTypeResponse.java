package io.github.kamill7779.qforge.exam.dto.exam;

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
