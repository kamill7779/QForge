package io.github.kamill7779.qforge.gaokaoanalysis.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.GaokaoIndexCallbackRequest;
import io.github.kamill7779.qforge.common.contract.GaokaoIndexingConstants;
import io.github.kamill7779.qforge.common.contract.GaokaoPaperIndexRequestedEvent;
import io.github.kamill7779.qforge.gaokaoanalysis.client.GaokaoCorpusClient;
import io.github.kamill7779.qforge.gaokaoanalysis.config.QdrantProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.BuildVectorRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.RecommendedQuestionDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.service.VectorService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class GaokaoPaperIndexRequestedConsumer {

    private static final Logger log = LoggerFactory.getLogger(GaokaoPaperIndexRequestedConsumer.class);

    private final VectorService vectorService;
    private final GaokaoCorpusClient gaokaoCorpusClient;
    private final QdrantProperties qdrantProperties;
    private final ObjectMapper objectMapper;

    public GaokaoPaperIndexRequestedConsumer(
            VectorService vectorService,
            GaokaoCorpusClient gaokaoCorpusClient,
            QdrantProperties qdrantProperties,
            ObjectMapper objectMapper
    ) {
        this.vectorService = vectorService;
        this.gaokaoCorpusClient = gaokaoCorpusClient;
        this.qdrantProperties = qdrantProperties;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = GaokaoIndexingConstants.PAPER_INDEX_REQUESTED_QUEUE)
    public void consume(GaokaoPaperIndexRequestedEvent event) {
        log.info("Consuming gaokao paper index event: paperUuid={}, questionCount={}",
                event.paperUuid(), event.questions() != null ? event.questions().size() : 0);

        List<GaokaoIndexCallbackRequest.RagChunkPayload> ragChunks = new ArrayList<>();
        List<GaokaoIndexCallbackRequest.VectorPointPayload> vectorPoints = new ArrayList<>();
        List<GaokaoIndexCallbackRequest.RecommendEdgePayload> recommendEdges = new ArrayList<>();

        try {
            List<GaokaoPaperIndexRequestedEvent.QuestionPayload> questions =
                    event.questions() == null ? List.of() : event.questions();
            for (GaokaoPaperIndexRequestedEvent.QuestionPayload question : questions) {
                BuildVectorRequest request = new BuildVectorRequest();
                request.setQuestionId(question.questionId());
                request.setQuestionUuid(question.questionUuid());
                request.setStemText(question.stemText());
                request.setNormalizedStemText(question.normalizedStemText());
                request.setAnalysisSummaryText(question.analysisSummaryText());
                request.setQuestionTypeCode(question.questionTypeCode());
                request.setDifficultyLevel(question.difficultyLevel());
                request.setExamYear(event.examYear());
                request.setProvinceCode(event.provinceCode());
                vectorService.buildQuestionVectors(request);

                String questionPayloadJson = toJson(Map.of(
                        "paperUuid", event.paperUuid(),
                        "questionId", question.questionId(),
                        "questionUuid", question.questionUuid(),
                        "questionTypeCode", question.questionTypeCode()
                ));
                vectorPoints.add(new GaokaoIndexCallbackRequest.VectorPointPayload(
                        "QUESTION",
                        question.questionId(),
                        "stem",
                        qdrantProperties.getQuestionCollection(),
                        "question-" + question.questionId(),
                        questionPayloadJson,
                        "ACTIVE"
                ));
                vectorPoints.add(new GaokaoIndexCallbackRequest.VectorPointPayload(
                        "QUESTION",
                        question.questionId(),
                        "analysis",
                        qdrantProperties.getQuestionCollection(),
                        "question-" + question.questionId(),
                        questionPayloadJson,
                        "ACTIVE"
                ));
                vectorPoints.add(new GaokaoIndexCallbackRequest.VectorPointPayload(
                        "QUESTION",
                        question.questionId(),
                        "joint",
                        qdrantProperties.getQuestionCollection(),
                        "question-" + question.questionId(),
                        questionPayloadJson,
                        "ACTIVE"
                ));

                appendChunk(question, "STEM", question.stemText(), ragChunks, vectorPoints);
                appendChunk(question, "ANALYSIS", question.analysisSummaryText(), ragChunks, vectorPoints);
                if (question.answers() != null) {
                    for (GaokaoPaperIndexRequestedEvent.AnswerPayload answer : question.answers()) {
                        appendChunk(question, "ANSWER", answer.answerText(), ragChunks, vectorPoints);
                    }
                }
            }

            for (GaokaoPaperIndexRequestedEvent.QuestionPayload question : questions) {
                List<RecommendedQuestionDTO> recommendations =
                        vectorService.searchSimilar(question.normalizedStemText(), Map.of(), 4);
                for (RecommendedQuestionDTO recommendation : recommendations) {
                    if (recommendation.getQuestionId() == null
                            || recommendation.getQuestionId().equals(question.questionId())) {
                        continue;
                    }
                    recommendEdges.add(new GaokaoIndexCallbackRequest.RecommendEdgePayload(
                            question.questionId(),
                            recommendation.getQuestionId(),
                            "SAME_CLASS",
                            recommendation.getScore() != null ? recommendation.getScore() : BigDecimal.ZERO
                    ));
                }
            }

            gaokaoCorpusClient.updatePaperIndex(
                    event.paperId(),
                    new GaokaoIndexCallbackRequest(
                            event.paperId(),
                            event.paperUuid(),
                            "READY",
                            null,
                            ragChunks,
                            vectorPoints,
                            recommendEdges
                    )
            );
        } catch (Exception ex) {
            log.error("Gaokao paper indexing failed: paperUuid={}", event.paperUuid(), ex);
            gaokaoCorpusClient.updatePaperIndex(
                    event.paperId(),
                    new GaokaoIndexCallbackRequest(
                            event.paperId(),
                            event.paperUuid(),
                            "INDEX_FAILED",
                            ex.getMessage(),
                            List.of(),
                            List.of(),
                            List.of()
                    )
            );
        }
    }

    private void appendChunk(
            GaokaoPaperIndexRequestedEvent.QuestionPayload question,
            String chunkType,
            String chunkText,
            List<GaokaoIndexCallbackRequest.RagChunkPayload> ragChunks,
            List<GaokaoIndexCallbackRequest.VectorPointPayload> vectorPoints) {
        if (chunkText == null || chunkText.isBlank()) {
            return;
        }
        String suffix = chunkType.toLowerCase() + "-" + (ragChunks.size() + 1);
        String chunkUuid = question.questionUuid() + "-" + suffix;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("questionId", question.questionId());
        payload.put("questionUuid", question.questionUuid());
        payload.put("chunkType", chunkType);
        payload.put("chunkText", chunkText);
        vectorService.upsertChunkVector(chunkUuid, chunkText, payload);
        ragChunks.add(new GaokaoIndexCallbackRequest.RagChunkPayload(
                question.questionId(),
                chunkUuid,
                chunkType,
                chunkText,
                estimateTokens(chunkText)
        ));
        vectorPoints.add(new GaokaoIndexCallbackRequest.VectorPointPayload(
                "RAG_CHUNK",
                question.questionId(),
                chunkType.toLowerCase(),
                qdrantProperties.getChunkCollection(),
                "chunk-" + chunkUuid,
                toJson(payload),
                "ACTIVE"
        ));
    }

    private int estimateTokens(String text) {
        return text == null || text.isBlank() ? 0 : Math.max(1, text.length() / 4);
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
