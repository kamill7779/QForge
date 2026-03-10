package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.config.QForgeAnalysisProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.service.RerankerService;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RerankerServiceImpl implements RerankerService {

    private static final Logger log = LoggerFactory.getLogger(RerankerServiceImpl.class);
    private final QForgeAnalysisProperties analysisProperties;

    public RerankerServiceImpl(QForgeAnalysisProperties analysisProperties) {
        this.analysisProperties = analysisProperties;
    }

    @Override
    public List<RecommendedQuestionDTO> rerank(String queryStem, List<RecommendedQuestionDTO> candidates) {
        log.info("rerank: queryStemLen={}, candidates={} (pass-through sort by score desc)",
                queryStem != null ? queryStem.length() : 0, candidates != null ? candidates.size() : 0);
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        return candidates.stream()
                .sorted(Comparator.comparing(
                        RecommendedQuestionDTO::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, analysisProperties.getRerankTopK()))
                .collect(Collectors.toList());
    }
}
