package io.github.kamill7779.qforge.exam.dto.export;

import java.util.List;
import java.util.Map;

public record ExportQuestionPayload(
        String questionUuid,
        String stemText,
        Double difficulty,
        List<ExportAnswerPayload> answers,
        List<ExportAssetPayload> stemAssets,
        List<Map<String, String>> mainTags,
        List<String> secondaryTags
) {
}
