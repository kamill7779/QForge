package io.github.kamill7779.qforge.gaokaoanalysis.service;

import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalysisResultDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import java.util.List;

public interface RagService {

    /**
     * Generate a human-readable recommendation reason using RAG.
     */
    String generateRecommendReason(AnalysisResultDTO queryProfile, List<RecommendedQuestionDTO> recommendations);
}
