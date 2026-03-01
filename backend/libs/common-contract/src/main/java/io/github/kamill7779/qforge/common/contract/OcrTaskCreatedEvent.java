package io.github.kamill7779.qforge.common.contract;

public record OcrTaskCreatedEvent(
        String taskUuid,
        String bizType,
        String bizId,
        String imageBase64,
        String requestUser,
        String createdAt
) {
}

