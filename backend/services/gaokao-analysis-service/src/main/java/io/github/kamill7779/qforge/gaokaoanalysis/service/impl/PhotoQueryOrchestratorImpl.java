package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.client.OcrServiceClient;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.PhotoQueryInternalRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.PhotoQueryInternalResponse;
import io.github.kamill7779.qforge.gaokaoanalysis.service.AiAnalysisService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.PhotoQueryOrchestrator;
import io.github.kamill7779.qforge.gaokaoanalysis.service.RagService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.RerankerService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.TextCleansingService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.VectorService;
import org.springframework.stereotype.Service;

@Service
public class PhotoQueryOrchestratorImpl implements PhotoQueryOrchestrator {

    private final OcrServiceClient ocrServiceClient;
    private final TextCleansingService textCleansingService;
    private final AiAnalysisService aiAnalysisService;
    private final VectorService vectorService;
    private final RerankerService rerankerService;
    private final RagService ragService;

    public PhotoQueryOrchestratorImpl(
            OcrServiceClient ocrServiceClient,
            TextCleansingService textCleansingService,
            AiAnalysisService aiAnalysisService,
            VectorService vectorService,
            RerankerService rerankerService,
            RagService ragService
    ) {
        this.ocrServiceClient = ocrServiceClient;
        this.textCleansingService = textCleansingService;
        this.aiAnalysisService = aiAnalysisService;
        this.vectorService = vectorService;
        this.rerankerService = rerankerService;
        this.ragService = ragService;
    }

    @Override
    public PhotoQueryInternalResponse process(PhotoQueryInternalRequest request) {
        // TODO: implement — orchestrate OCR → cleanse → XML → analysis → vector search → rerank → RAG
        throw new UnsupportedOperationException("PhotoQueryOrchestrator.process not implemented");
    }
}
