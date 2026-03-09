package io.github.kamill7779.qforge.gaokaoanalysis.service;

import io.github.kamill7779.qforge.gaokaoanalysis.dto.PhotoQueryInternalRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.PhotoQueryInternalResponse;

public interface PhotoQueryOrchestrator {

    /**
     * Full pipeline: OCR → cleanse → XML → analysis → vector search → rerank → RAG reason.
     */
    PhotoQueryInternalResponse process(PhotoQueryInternalRequest request);
}
