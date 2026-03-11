package io.github.kamill7779.qforge.internal.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * gaokao-corpus-service 调用 ocr-service 时使用的整卷分题请求。
 */
public record GaokaoSplitRequest(
        @NotEmpty List<@Valid SourceFileEntry> sourceFiles,
        boolean hasAnswerHint
) {

    public record SourceFileEntry(
            Integer fileIndex,
            String fileName,
            String fileType,
            String imageBase64,
            String storageRef
    ) {
    }
}