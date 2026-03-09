package io.github.kamill7779.qforge.internal.api;

import java.math.BigDecimal;
import java.util.List;

/**
 * 题目完整数据 — 含答案 + 资产 + 标签，供 exam-service 导出 Word 使用。
 */
public record QuestionFullDTO(
        String questionUuid,
        String stemText,
        BigDecimal difficulty,
        String source,
        List<AnswerDTO> answers,
        List<AssetDTO> stemAssets,
        List<MainTagDTO> mainTags,
        List<String> secondaryTags
) {

    public record AnswerDTO(
            String answerUuid,
            String latexText,
            int sortOrder,
            boolean official,
            List<AssetDTO> assets
    ) {}

    public record AssetDTO(
            String refKey,
            String imageData,
            String mimeType
    ) {}

    public record MainTagDTO(
            String categoryCode,
            String categoryName,
            String tagCode,
            String tagName
    ) {}
}
