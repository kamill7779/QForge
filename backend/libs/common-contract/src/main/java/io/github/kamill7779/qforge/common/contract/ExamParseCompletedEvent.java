package io.github.kamill7779.qforge.common.contract;

/**
 * 整卷解析完成事件 —— ocr-service → question-service（全部题目处理完毕后发送）。
 */
public record ExamParseCompletedEvent(
        String taskUuid,
        String status,
        int questionCount,
        String errorMsg
) {
}
