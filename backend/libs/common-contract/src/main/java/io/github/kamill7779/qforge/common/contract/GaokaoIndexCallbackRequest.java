package io.github.kamill7779.qforge.common.contract;

import java.math.BigDecimal;
import java.util.List;

public record GaokaoIndexCallbackRequest(
        Long paperId,
        String paperUuid,
        String status,
        String errorMessage,
        List<RagChunkPayload> ragChunks,
        List<VectorPointPayload> vectorPoints,
        List<RecommendEdgePayload> recommendEdges
) {

    public record RagChunkPayload(
            Long questionId,
            String chunkUuid,
            String chunkType,
            String chunkText,
            Integer tokenCount
    ) {
    }

    public record VectorPointPayload(
            String targetType,
            Long targetId,
            String vectorKind,
            String collectionName,
            String qdrantPointId,
            String payloadJson,
            String status
    ) {
    }

    public record RecommendEdgePayload(
            Long sourceQuestionId,
            Long targetQuestionId,
            String relationType,
            BigDecimal score
    ) {
    }
}
