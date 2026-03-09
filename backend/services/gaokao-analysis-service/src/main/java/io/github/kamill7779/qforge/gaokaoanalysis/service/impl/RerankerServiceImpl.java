package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.service.RerankerService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class RerankerServiceImpl implements RerankerService {

    @Override
    public List<RecommendedQuestionDTO> rerank(String queryStem, List<RecommendedQuestionDTO> candidates) {
        // TODO: implement — call reranker model API, sort candidates by new score
        throw new UnsupportedOperationException("RerankerService.rerank not implemented");
    }
}
