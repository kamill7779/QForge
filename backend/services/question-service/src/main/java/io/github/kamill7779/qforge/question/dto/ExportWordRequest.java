package io.github.kamill7779.qforge.question.dto;

import io.github.kamill7779.qforge.question.dto.export.ExportSectionPayload;
import java.util.List;

/**
 * 前端发起的 Word 导出请求。
 */
public record ExportWordRequest(
        List<String> questionUuids,
        String title,
        boolean includeAnswers,
        String answerPosition,
        List<ExportSectionPayload> sections
) {
    /**
     * 安全获取标题，默认 "题目导出"。
     */
    public String safeTitle() {
        return title != null && !title.isBlank() ? title : "题目导出";
    }

    /**
     * 安全获取答案位置，默认 AFTER_ALL。
     */
    public String safeAnswerPosition() {
        return answerPosition != null && !answerPosition.isBlank() ? answerPosition : "AFTER_ALL";
    }
}
