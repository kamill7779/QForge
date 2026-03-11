package io.github.kamill7779.qforge.internal.api;

import java.util.List;
import java.util.Map;

/**
 * ocr-service 返回给 gaokao-corpus-service 的整卷分题结果。
 */
public record GaokaoSplitResponse(
        List<SplitQuestionEntry> questions,
        int totalPages,
        String errorMsg
) {

    public record SplitQuestionEntry(
            int seq,
            String questionTypeCode,
            List<Integer> sourcePages,
            String rawStemText,
            String stemXml,
            String rawAnswerText,
            String answerXml,
            List<String> stemImageRefs,
            List<String> answerImageRefs,
            Map<String, String> stemImages,
            Map<String, String> answerImages,
            boolean parseError,
            String errorMsg
    ) {
    }
}