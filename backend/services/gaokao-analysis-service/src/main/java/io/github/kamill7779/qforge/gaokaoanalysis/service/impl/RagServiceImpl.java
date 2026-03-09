package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.config.QForgeAnalysisProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalysisResultDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.service.RagService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RagServiceImpl implements RagService {

    private final QForgeAnalysisProperties analysisProperties;

    public RagServiceImpl(QForgeAnalysisProperties analysisProperties) {
        this.analysisProperties = analysisProperties;
    }

    @Override
    public String generateRecommendReason(AnalysisResultDTO queryProfile, List<RecommendedQuestionDTO> recommendations) {
        // TODO: implement — retrieve RAG chunks, build prompt, call AI model
        throw new UnsupportedOperationException("RagService.generateRecommendReason not implemented");
    }
}
