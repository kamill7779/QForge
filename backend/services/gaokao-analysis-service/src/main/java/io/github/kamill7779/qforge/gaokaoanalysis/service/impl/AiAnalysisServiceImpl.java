package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.config.QForgeAnalysisProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalysisResultDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalyzePaperRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalyzeQuestionRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.service.AiAnalysisService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private final QForgeAnalysisProperties analysisProperties;

    public AiAnalysisServiceImpl(QForgeAnalysisProperties analysisProperties) {
        this.analysisProperties = analysisProperties;
    }

    @Override
    public AnalysisResultDTO analyzeQuestion(AnalyzeQuestionRequest request) {
        // TODO: implement — call AI model, parse tags/difficulty/reasoning
        throw new UnsupportedOperationException("AiAnalysisService.analyzeQuestion not implemented");
    }

    @Override
    public List<AnalysisResultDTO> analyzePaper(AnalyzePaperRequest request) {
        // TODO: implement — iterate questions, call analyzeQuestion for each
        throw new UnsupportedOperationException("AiAnalysisService.analyzePaper not implemented");
    }
}
