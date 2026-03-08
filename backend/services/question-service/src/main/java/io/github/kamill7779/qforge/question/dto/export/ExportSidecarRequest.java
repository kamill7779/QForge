package io.github.kamill7779.qforge.question.dto.export;

import java.util.List;

/**
 * 发送到 export-sidecar 的完整导出请求。
 */
public record ExportSidecarRequest(
        List<ExportQuestionPayload> questions,
        String title,
        boolean includeAnswers,
        String answerPosition,
        List<ExportSectionPayload> sections
) {
}
