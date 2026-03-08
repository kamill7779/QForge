package io.github.kamill7779.qforge.question.dto.export;

import java.util.List;

/**
 * 导出答案数据。
 */
public record ExportAnswerPayload(
        String answerUuid,
        String latexText,
        int sortOrder,
        List<ExportAssetPayload> assets
) {
}
