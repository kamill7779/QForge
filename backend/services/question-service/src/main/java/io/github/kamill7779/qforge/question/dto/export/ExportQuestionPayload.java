package io.github.kamill7779.qforge.question.dto.export;

import java.util.List;
import java.util.Map;

/**
 * 导出题目完整数据 — 与 export-sidecar 的 QuestionPayload 对齐。
 */
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
