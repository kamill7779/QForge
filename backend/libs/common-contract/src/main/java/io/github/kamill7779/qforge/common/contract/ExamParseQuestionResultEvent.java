package io.github.kamill7779.qforge.common.contract;

import java.util.List;

/**
 * 单题解析结果事件 —— ocr-service → question-service（逐题推送）。
 */
public record ExamParseQuestionResultEvent(
        String taskUuid,
        int seqNo,
        String questionType,
        String rawStemText,
        String stemXml,
        String rawAnswerText,
        String answerXml,
        List<ExtractedImage> stemImages,
        List<ExtractedImage> answerImages,
        List<Integer> sourcePages,
        boolean parseError,
        String errorMsg
) {
}
