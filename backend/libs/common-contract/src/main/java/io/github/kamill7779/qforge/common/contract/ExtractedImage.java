package io.github.kamill7779.qforge.common.contract;

/**
 * OCR 图片裁剪结果 DTO —— 从原始图片中按 bbox 裁剪得到的单张图片。
 *
 * @param refKey      对应 stem XML 中的引用 key，如 "fig-1"
 * @param imageBase64 裁剪后的图片 base64 编码（不含 data URL 前缀）
 * @param mimeType    MIME 类型，通常为 "image/png"
 */
public record ExtractedImage(
        String refKey,
        String imageBase64,
        String mimeType
) {
}
