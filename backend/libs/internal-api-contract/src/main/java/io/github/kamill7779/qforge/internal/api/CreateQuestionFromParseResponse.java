package io.github.kamill7779.qforge.internal.api;

/**
 * question-core 创建题目后返回的结果。
 */
public record CreateQuestionFromParseResponse(
        String questionUuid,
        long questionId
) {}
