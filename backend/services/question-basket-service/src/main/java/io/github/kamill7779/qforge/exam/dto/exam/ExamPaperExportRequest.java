package io.github.kamill7779.qforge.exam.dto.exam;

public record ExamPaperExportRequest(
        boolean includeAnswers,
        String answerPosition
) {
    public String safeAnswerPosition() {
        return answerPosition != null && !answerPosition.isBlank() ? answerPosition : "AFTER_ALL";
    }
}
