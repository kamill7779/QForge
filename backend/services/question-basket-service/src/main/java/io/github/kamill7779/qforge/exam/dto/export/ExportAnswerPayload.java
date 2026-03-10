package io.github.kamill7779.qforge.exam.dto.export;

import java.util.List;

public record ExportAnswerPayload(
        String answerUuid,
        String latexText,
        int sortOrder,
        List<ExportAssetPayload> assets
) {
}
