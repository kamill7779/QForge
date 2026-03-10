package io.github.kamill7779.qforge.exam.dto;

import io.github.kamill7779.qforge.exam.dto.export.ExportSectionPayload;
import java.util.List;

public record ExportWordRequest(
        List<String> questionUuids,
        String title,
        boolean includeAnswers,
        String answerPosition,
        List<ExportSectionPayload> sections
) {
    public String safeTitle() {
        return title != null && !title.isBlank() ? title : "题目导出";
    }

    public String safeAnswerPosition() {
        return answerPosition != null && !answerPosition.isBlank() ? answerPosition : "AFTER_ALL";
    }
}
