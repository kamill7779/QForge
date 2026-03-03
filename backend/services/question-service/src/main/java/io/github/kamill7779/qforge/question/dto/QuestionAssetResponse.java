package io.github.kamill7779.qforge.question.dto;

/**
 * 题目图片资产的查询响应。
 *
 * @param assetUuid  资产唯一 UUID
 * @param refKey     前端 XML 中的引用 key（如 img-1）
 * @param imageData  图片 base64 编码数据
 * @param mimeType   MIME 类型（image/png 等）
 */
public record QuestionAssetResponse(
        String assetUuid,
        String refKey,
        String imageData,
        String mimeType
) {
}
