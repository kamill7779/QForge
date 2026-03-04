package io.github.kamill7779.qforge.question.dto;

/**
 * 新增答案接口返回值，包含生成的 answerUuid 供前端管理答案图片。
 */
public record AddAnswerResponse(
        String questionUuid,
        String status,
        String answerUuid
) {
}
