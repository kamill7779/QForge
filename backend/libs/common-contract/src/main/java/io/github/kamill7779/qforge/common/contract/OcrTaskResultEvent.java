package io.github.kamill7779.qforge.common.contract;

/**
 * OCR 任务结果事件 —— ocr-service → question-service。
 *
 * @param extractedImagesJson 裁剪后的图片 JSON 数组（{@link ExtractedImage} 列表），
 *                            仅 QUESTION_STEM 且 OCR 检测到 bbox 时非 null。
 */
public record OcrTaskResultEvent(
        String taskUuid,
        String bizType,
        String bizId,
        String status,
        String recognizedText,
        String errorCode,
        String errorMessage,
        String requestUser,
        String finishedAt,
        String extractedImagesJson
) {
}

