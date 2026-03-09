package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.config.QForgeAnalysisProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.config.QdrantProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.BuildVectorRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.service.VectorService;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class VectorServiceImpl implements VectorService {

    private final QForgeAnalysisProperties analysisProperties;
    private final QdrantProperties qdrantProperties;

    public VectorServiceImpl(QForgeAnalysisProperties analysisProperties, QdrantProperties qdrantProperties) {
        this.analysisProperties = analysisProperties;
        this.qdrantProperties = qdrantProperties;
    }

    @Override
    public void buildQuestionVectors(BuildVectorRequest request) {
        // TODO: implement — embed text via embedding model, upsert to qdrant question collection
        throw new UnsupportedOperationException("VectorService.buildQuestionVectors not implemented");
    }

    @Override
    public void buildChunkVectors(Long questionId) {
        // TODO: implement — split question into chunks, embed, upsert to qdrant chunk collection
        throw new UnsupportedOperationException("VectorService.buildChunkVectors not implemented");
    }

    @Override
    public List<RecommendedQuestionDTO> searchSimilar(String stemText, Map<String, Object> filters, int topK) {
        // TODO: implement — embed query, search qdrant, map results
        throw new UnsupportedOperationException("VectorService.searchSimilar not implemented");
    }
}
