package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.config.QForgeAnalysisProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalysisResultDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.service.RagService;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RagServiceImpl implements RagService {

    private static final Logger log = LoggerFactory.getLogger(RagServiceImpl.class);

    private final QForgeAnalysisProperties analysisProperties;

    public RagServiceImpl(QForgeAnalysisProperties analysisProperties) {
        this.analysisProperties = analysisProperties;
    }

    @Override
    public String generateRecommendReason(AnalysisResultDTO queryProfile, List<RecommendedQuestionDTO> recommendations) {
        log.info("generateRecommendReason: model={}, recommendCount={} (stub)",
                analysisProperties.getAiModel(), recommendations != null ? recommendations.size() : 0);
        if (recommendations == null || recommendations.isEmpty()) {
            return "暂无推荐题目。";
        }
        return "基于本题知识点与解法分析，推荐以上相似题目供练习巩固。";
    }
}
