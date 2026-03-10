package io.github.kamill7779.qforge.exam.dto.export;

import java.util.List;

public record ExportSidecarRequest(
        List<ExportQuestionPayload> questions,
        String title,
        boolean includeAnswers,
        String answerPosition,
        List<ExportSectionPayload> sections
) {
}
