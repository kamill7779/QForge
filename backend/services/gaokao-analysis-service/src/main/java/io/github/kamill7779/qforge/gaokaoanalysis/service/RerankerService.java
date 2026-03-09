package io.github.kamill7779.qforge.gaokaoanalysis.service;

import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import java.util.List;

public interface RerankerService {

    /**
     * Re-rank candidate questions by relevance to query stem.
     */
    List<RecommendedQuestionDTO> rerank(String queryStem, List<RecommendedQuestionDTO> candidates);
}
