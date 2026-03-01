package io.github.kamill7779.qforge.common.contract;

public record OcrTaskResultEvent(
        String taskUuid,
        String bizType,
        String bizId,
        String status,
        String recognizedText,
        String errorCode,
        String errorMessage,
        String requestUser,
        String finishedAt
) {
}

