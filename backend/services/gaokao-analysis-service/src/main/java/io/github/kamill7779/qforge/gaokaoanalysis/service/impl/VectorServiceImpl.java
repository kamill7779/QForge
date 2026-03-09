package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.config.QForgeAnalysisProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.config.QdrantProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.BuildVectorRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.service.VectorService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VectorServiceImpl implements VectorService {

    private static final Logger log = LoggerFactory.getLogger(VectorServiceImpl.class);

    private final QForgeAnalysisProperties analysisProperties;
    private final QdrantProperties qdrantProperties;

    public VectorServiceImpl(QForgeAnalysisProperties analysisProperties, QdrantProperties qdrantProperties) {
        this.analysisProperties = analysisProperties;
        this.qdrantProperties = qdrantProperties;
    }

    @Override
    public void buildQuestionVectors(BuildVectorRequest request) {
        log.info("buildQuestionVectors: questionId={}, collection={}, embeddingModel={} (stub — Qdrant integration pending)",
                request.getQuestionId(), qdrantProperties.getQuestionCollection(), analysisProperties.getEmbeddingModel());
    }

    @Override
    public void buildChunkVectors(Long questionId) {
        log.info("buildChunkVectors: questionId={}, collection={} (stub — Qdrant integration pending)",
                questionId, qdrantProperties.getChunkCollection());
    }

    @Override
    public List<RecommendedQuestionDTO> searchSimilar(String stemText, Map<String, Object> filters, int topK) {
        log.info("searchSimilar: topK={}, stemLen={} (stub — returning empty)", topK,
                stemText != null ? stemText.length() : 0);
        return Collections.emptyList();
    }
}
