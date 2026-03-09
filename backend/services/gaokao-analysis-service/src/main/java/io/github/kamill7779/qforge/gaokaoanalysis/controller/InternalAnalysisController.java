package io.github.kamill7779.qforge.gaokaoanalysis.controller;

import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalysisResultDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalyzePaperRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalyzeQuestionRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.PhotoQueryInternalRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.PhotoQueryInternalResponse;
import io.github.kamill7779.qforge.gaokaoanalysis.service.AiAnalysisService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.PhotoQueryOrchestrator;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/gaokao-analysis")
public class InternalAnalysisController {

    private final AiAnalysisService aiAnalysisService;
    private final PhotoQueryOrchestrator photoQueryOrchestrator;

    public InternalAnalysisController(
            AiAnalysisService aiAnalysisService,
            PhotoQueryOrchestrator photoQueryOrchestrator
    ) {
        this.aiAnalysisService = aiAnalysisService;
        this.photoQueryOrchestrator = photoQueryOrchestrator;
    }

    @PostMapping("/analyze-question")
    public ResponseEntity<AnalysisResultDTO> analyzeQuestion(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody AnalyzeQuestionRequest request
    ) {
        AnalysisResultDTO result = aiAnalysisService.analyzeQuestion(request);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/analyze-paper")
    public ResponseEntity<List<AnalysisResultDTO>> analyzePaper(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @Valid @RequestBody AnalyzePaperRequest request
    ) {
        List<AnalysisResultDTO> results = aiAnalysisService.analyzePaper(request);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/photo-query")
    public ResponseEntity<PhotoQueryInternalResponse> photoQuery(
            @RequestHeader(value = "X-User-Id", required = false) String userId,
            @RequestBody PhotoQueryInternalRequest request
    ) {
        PhotoQueryInternalResponse response = photoQueryOrchestrator.process(request);
        return ResponseEntity.ok(response);
    }
}
