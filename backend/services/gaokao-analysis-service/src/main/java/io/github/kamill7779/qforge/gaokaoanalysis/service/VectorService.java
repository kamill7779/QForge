package io.github.kamill7779.qforge.gaokaoanalysis.service;

import io.github.kamill7779.qforge.gaokaoanalysis.dto.BuildVectorRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import java.util.List;
import java.util.Map;

public interface VectorService {

    /**
     * Build and upsert question-level vectors into Qdrant.
     */
    void buildQuestionVectors(BuildVectorRequest request);

    /**
     * Build and upsert chunk-level vectors for RAG.
     */
    void buildChunkVectors(Long questionId);

    void upsertChunkVector(String chunkUuid, String chunkText, Map<String, Object> payload);

    /**
     * Search similar questions via vector similarity + optional metadata filters.
     */
    List<RecommendedQuestionDTO> searchSimilar(String stemText, Map<String, Object> filters, int topK);
}
