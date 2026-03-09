package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.client.OcrRecognizeRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.client.OcrRecognizeResponse;
import io.github.kamill7779.qforge.gaokaoanalysis.client.OcrServiceClient;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalysisResultDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalyzeQuestionRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.PhotoQueryInternalRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.PhotoQueryInternalResponse;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendGroupDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.service.AiAnalysisService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.PhotoQueryOrchestrator;
import io.github.kamill7779.qforge.gaokaoanalysis.service.RagService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.RerankerService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.TextCleansingService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.VectorService;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PhotoQueryOrchestratorImpl implements PhotoQueryOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(PhotoQueryOrchestratorImpl.class);

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
        log.info("PhotoQuery pipeline started");

        // Step 1: OCR — get raw text from image
        String rawText = request.getOcrText();
        if ((rawText == null || rawText.isBlank()) && request.getImageBase64() != null) {
            try {
                OcrRecognizeRequest ocrReq = new OcrRecognizeRequest();
                ocrReq.setImageBase64(request.getImageBase64());
                ocrReq.setFileType("PNG");
                OcrRecognizeResponse ocrResp = ocrServiceClient.recognize(ocrReq);
                rawText = ocrResp.getFullText();
                log.info("OCR completed: {} chars", rawText != null ? rawText.length() : 0);
            } catch (Exception e) {
                log.warn("OCR service unavailable, using empty text: {}", e.getMessage());
                rawText = "";
            }
        }
        if (rawText == null) rawText = "";

        // Step 2: Cleanse text
        String cleaned = textCleansingService.cleanStemText(rawText);
        String stemXml = textCleansingService.convertToXml(cleaned);
        String normalizedForSearch = textCleansingService.normalizeForSearch(cleaned);

        // Step 3: AI analysis (no draftQuestionId for photo query)
        AnalyzeQuestionRequest analyzeReq = new AnalyzeQuestionRequest();
        analyzeReq.setDraftQuestionId(0L);
        analyzeReq.setStemText(cleaned);
        analyzeReq.setStemXml(stemXml);
        AnalysisResultDTO analysisProfile = aiAnalysisService.analyzeQuestion(analyzeReq);

        // Step 4: Vector search
        List<RecommendedQuestionDTO> candidates = vectorService.searchSimilar(normalizedForSearch, Collections.emptyMap(), 10);

        // Step 5: Rerank
        List<RecommendedQuestionDTO> reranked = rerankerService.rerank(cleaned, candidates);

        // Step 6: RAG reason
        String reason = ragService.generateRecommendReason(analysisProfile, reranked);

        // Build response
        PhotoQueryInternalResponse response = new PhotoQueryInternalResponse();
        response.setStemText(cleaned);
        response.setStemXml(stemXml);
        response.setAnalysisProfile(analysisProfile);
        RecommendGroupDTO group = new RecommendGroupDTO();
        group.setRelationType("SAME_CLASS");
        group.setQuestions(reranked);
        response.setRecommendGroups(List.of(group));
        response.setReasonSummary(reason);

        log.info("PhotoQuery pipeline completed: {} results", reranked.size());
        return response;
    }
}
