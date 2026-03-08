package io.github.kamill7779.qforge.question.dto.exam;

/**
 * 试卷导出 Word 请求。
 */
public record ExamPaperExportRequest(
        boolean includeAnswers,
        String answerPosition
) {
    public String safeAnswerPosition() {
        return answerPosition != null && !answerPosition.isBlank() ? answerPosition : "AFTER_ALL";
    }
}
