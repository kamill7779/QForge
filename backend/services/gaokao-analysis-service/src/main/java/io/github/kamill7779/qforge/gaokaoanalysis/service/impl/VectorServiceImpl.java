package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.config.QForgeAnalysisProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.config.QdrantProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.BuildVectorRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.service.VectorService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Service
@SuppressWarnings("unchecked")
public class VectorServiceImpl implements VectorService {

    private static final Logger log = LoggerFactory.getLogger(VectorServiceImpl.class);

    private final EmbeddingModel embeddingModel;
    private final QForgeAnalysisProperties analysisProperties;
    private final QdrantProperties qdrantProperties;
    private final RestClient restClient;
    private final Set<String> ensuredCollections = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public VectorServiceImpl(
            ObjectProvider<EmbeddingModel> embeddingModelProvider,
            QForgeAnalysisProperties analysisProperties,
            QdrantProperties qdrantProperties
    ) {
        this.embeddingModel = embeddingModelProvider.getIfAvailable();
        this.analysisProperties = analysisProperties;
        this.qdrantProperties = qdrantProperties;
        this.restClient = RestClient.builder()
                .baseUrl("http://" + qdrantProperties.getHost() + ":" + qdrantProperties.getPort())
                .build();
    }

    @Override
    public void buildQuestionVectors(BuildVectorRequest request) {
        ensureQuestionCollection();
        float[] stemVector = embed(request.getNormalizedStemText() != null ? request.getNormalizedStemText() : request.getStemText());
        float[] analysisVector = embed(request.getAnalysisSummaryText());
        float[] jointVector = embed(joinParts(request.getStemText(), request.getAnalysisSummaryText()));
        if (jointVector.length == 0) {
            return;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", request.getQuestionId());
        payload.put("questionUuid", request.getQuestionUuid());
        payload.put("stemText", request.getStemText());
        payload.put("questionTypeCode", request.getQuestionTypeCode());
        payload.put("difficultyLevel", request.getDifficultyLevel());
        payload.put("examYear", request.getExamYear());
        payload.put("provinceCode", request.getProvinceCode());

        upsertPoint(
                qdrantProperties.getQuestionCollection(),
                questionPointId(request.getQuestionId()),
                Map.of(
                        "stem", toList(stemVector),
                        "analysis", toList(analysisVector),
                        "joint", toList(jointVector)
                ),
                payload
        );
    }

    @Override
    public void buildChunkVectors(Long questionId) {
        log.info("buildChunkVectors called for questionId={} without explicit chunk text; skipped", questionId);
    }

    @Override
    public void upsertChunkVector(String chunkUuid, String chunkText, Map<String, Object> payload) {
        ensureChunkCollection();
        float[] vector = embed(chunkText);
        if (vector.length == 0) {
            return;
        }
        upsertPoint(
                qdrantProperties.getChunkCollection(),
                chunkPointId(chunkUuid),
                Map.of("content", toList(vector)),
                payload
        );
    }

    @Override
    public List<RecommendedQuestionDTO> searchSimilar(String stemText, Map<String, Object> filters, int topK) {
        ensureQuestionCollection();
        float[] queryVector = embed(stemText);
        if (queryVector.length == 0) {
            return List.of();
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", toList(queryVector));
            body.put("using", "joint");
            body.put("limit", Math.max(1, Math.min(topK, Math.max(1, analysisProperties.getMaxSimilarResults()))));
            body.put("with_payload", true);

            if (filters != null && !filters.isEmpty()) {
                List<Map<String, Object>> must = new ArrayList<>();
                for (Map.Entry<String, Object> entry : filters.entrySet()) {
                    must.add(Map.of("key", entry.getKey(), "match", Map.of("value", entry.getValue())));
                }
                body.put("filter", Map.of("must", must));
            }

            Map<String, Object> response = restClient.post()
                    .uri("/collections/{collection}/points/query", qdrantProperties.getQuestionCollection())
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            List<Map<String, Object>> points = extractResultPoints(response);
            List<RecommendedQuestionDTO> results = new ArrayList<>();
            for (Map<String, Object> point : points) {
                Map<String, Object> payload = point.get("payload") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
                RecommendedQuestionDTO dto = new RecommendedQuestionDTO();
                dto.setQuestionId(longValue(payload.get("questionId")));
                dto.setQuestionUuid(stringValue(payload.get("questionUuid")));
                dto.setStemText(stringValue(payload.get("stemText")));
                dto.setQuestionTypeCode(stringValue(payload.get("questionTypeCode")));
                dto.setDifficultyLevel(stringValue(payload.get("difficultyLevel")));
                dto.setScore(decimalValue(point.get("score")));
                results.add(dto);
            }
            return results;
        } catch (Exception ex) {
            log.warn("Qdrant search failed, returning empty result: {}", ex.getMessage());
            return List.of();
        }
    }

    public String questionPointId(Long questionId) {
        return "question-" + questionId;
    }

    public String chunkPointId(String chunkUuid) {
        return "chunk-" + chunkUuid;
    }

    private float[] embed(String text) {
        if (embeddingModel == null || text == null || text.isBlank()) {
            return new float[0];
        }
        try {
            return embeddingModel.embed(text);
        } catch (Exception ex) {
            log.warn("Embedding request failed: {}", ex.getMessage());
            return new float[0];
        }
    }

    private void ensureQuestionCollection() {
        ensureCollection(qdrantProperties.getQuestionCollection(), Map.of(
                "stem", vectorDefinition(),
                "analysis", vectorDefinition(),
                "joint", vectorDefinition()
        ));
    }

    private void ensureChunkCollection() {
        ensureCollection(qdrantProperties.getChunkCollection(), Map.of(
                "content", vectorDefinition()
        ));
    }

    private Map<String, Object> vectorDefinition() {
        return Map.of(
                "size", analysisProperties.getEmbeddingDimension(),
                "distance", "Cosine"
        );
    }

    private void ensureCollection(String collectionName, Map<String, Object> vectors) {
        if (!ensuredCollections.add(collectionName)) {
            return;
        }
        try {
            restClient.put()
                    .uri("/collections/{collection}", collectionName)
                    .body(Map.of("vectors", vectors))
                    .retrieve()
                    .body(String.class);
            log.info("Created Qdrant collection={}", collectionName);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 409) {
                log.debug("Qdrant collection already exists: {}", collectionName);
                return;
            }
            ensuredCollections.remove(collectionName);
            log.warn("Failed to create Qdrant collection {}: {}", collectionName, ex.getMessage());
        } catch (RestClientException ex) {
            ensuredCollections.remove(collectionName);
            log.warn("Failed to create Qdrant collection {}: {}", collectionName, ex.getMessage());
        }
    }

    private void upsertPoint(String collectionName, String pointId, Map<String, Object> vector, Map<String, Object> payload) {
        try {
            restClient.put()
                    .uri(uriBuilder -> uriBuilder
                            .path("/collections/{collection}/points")
                            .queryParam("wait", true)
                            .build(collectionName))
                    .body(Map.of(
                            "points", List.of(Map.of(
                                    "id", pointId,
                                    "vector", vector,
                                    "payload", payload
                            ))
                    ))
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.warn("Qdrant upsert failed for collection={}, pointId={}: {}", collectionName, pointId, ex.getMessage());
        }
    }

    private List<Map<String, Object>> extractResultPoints(Map<String, Object> response) {
        if (response == null) {
            return List.of();
        }
        Object result = response.get("result");
        if (result instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        if (result instanceof Map<?, ?> map) {
            Object points = map.get("points");
            if (points instanceof List<?> list) {
                return (List<Map<String, Object>>) list;
            }
        }
        return List.of();
    }

    private List<Float> toList(float[] vector) {
        List<Float> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }

    private String joinParts(String first, String second) {
        StringBuilder builder = new StringBuilder();
        if (first != null && !first.isBlank()) {
            builder.append(first.trim());
        }
        if (second != null && !second.isBlank()) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(second.trim());
        }
        return builder.toString();
    }

    private String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal decimalValue(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private Long longValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }
}
