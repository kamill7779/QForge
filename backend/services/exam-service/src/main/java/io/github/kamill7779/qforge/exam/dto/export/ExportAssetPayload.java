package io.github.kamill7779.qforge.exam.dto.export;

public record ExportAssetPayload(
        String refKey,
        String imageData,
        String mimeType
) {
}
