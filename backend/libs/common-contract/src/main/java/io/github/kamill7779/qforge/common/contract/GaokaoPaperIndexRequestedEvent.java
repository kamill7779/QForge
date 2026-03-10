package io.github.kamill7779.qforge.common.contract;

import java.math.BigDecimal;
import java.util.List;

public record GaokaoPaperIndexRequestedEvent(
        String eventUuid,
        Long paperId,
        String paperUuid,
        String paperName,
        String examYear,
        String provinceCode,
        String requestedAt,
        List<QuestionPayload> questions
) {

    public record QuestionPayload(
            Long questionId,
            String questionUuid,
            String questionNo,
            String questionTypeCode,
            String answerMode,
            String stemText,
            String stemXml,
            String normalizedStemText,
            BigDecimal difficultyScore,
            String difficultyLevel,
            String knowledgeTagsJson,
            String methodTagsJson,
            String formulaTagsJson,
            String mistakeTagsJson,
            String abilityTagsJson,
            String reasoningStepsJson,
            String analysisSummaryText,
            List<AnswerPayload> answers
    ) {
    }

    public record AnswerPayload(
            Long answerId,
            String answerText,
            String answerXml,
            Boolean official,
            Integer sortOrder
    ) {
    }
}
