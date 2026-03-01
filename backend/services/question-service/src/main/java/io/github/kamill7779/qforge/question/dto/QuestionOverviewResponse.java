package io.github.kamill7779.qforge.question.dto;

import java.time.LocalDateTime;
import java.util.List;

public record QuestionOverviewResponse(
        String questionUuid,
        String status,
        String stemText,
        List<QuestionMainTagResponse> mainTags,
        List<String> secondaryTags,
        long answerCount,
        List<AnswerOverviewResponse> answers,
        LocalDateTime updatedAt
) {
}
