package io.github.kamill7779.qforge.question.dto.export;

/**
 * 导出资产数据 — 图片 base64。
 */
public record ExportAssetPayload(
        String refKey,
        String imageData,
        String mimeType
) {
}
