package io.github.kamill7779.qforge.question.client;

public record OcrServiceCreateTaskRequest(
        String bizType,
        String bizId,
        String imageBase64,
        String requestUser
) {
}

