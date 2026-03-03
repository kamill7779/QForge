package io.github.kamill7779.qforge.question.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record QuestionOverviewResponse(
        String questionUuid,
        String status,
        String stemText,
        List<QuestionMainTagResponse> mainTags,
        List<String> secondaryTags,
        BigDecimal difficulty,
        long answerCount,
        List<AnswerOverviewResponse> answers,
        LocalDateTime updatedAt,
        /** 该题目关联的全部内联图片（含完整 base64 数据）。 */
        List<QuestionAssetResponse> assets
) {
}
