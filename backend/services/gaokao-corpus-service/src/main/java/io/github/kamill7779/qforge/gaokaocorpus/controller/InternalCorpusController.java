package io.github.kamill7779.qforge.gaokaocorpus.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.kamill7779.qforge.common.contract.GaokaoIndexCallbackRequest;
import io.github.kamill7779.qforge.gaokaocorpus.dto.ProfileUpdateRequest;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftProfilePreview;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkPaper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkRagChunk;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkRecommendEdge;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkVectorPoint;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftProfilePreviewMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkRagChunkMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkRecommendEdgeMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkVectorPointMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/gaokao-corpus")
public class InternalCorpusController {

    private static final Logger log = LoggerFactory.getLogger(InternalCorpusController.class);

    private final GkDraftProfilePreviewMapper profileMapper;
    private final GkPaperMapper paperMapper;
    private final GkRagChunkMapper ragChunkMapper;
    private final GkVectorPointMapper vectorPointMapper;
    private final GkRecommendEdgeMapper recommendEdgeMapper;

    public InternalCorpusController(
            GkDraftProfilePreviewMapper profileMapper,
            GkPaperMapper paperMapper,
            GkRagChunkMapper ragChunkMapper,
            GkVectorPointMapper vectorPointMapper,
            GkRecommendEdgeMapper recommendEdgeMapper
    ) {
        this.profileMapper = profileMapper;
        this.paperMapper = paperMapper;
        this.ragChunkMapper = ragChunkMapper;
        this.vectorPointMapper = vectorPointMapper;
        this.recommendEdgeMapper = recommendEdgeMapper;
    }

    @PutMapping("/draft-questions/{draftQuestionId}/profile")
    public ResponseEntity<Void> updateDraftProfile(
            @PathVariable Long draftQuestionId,
            @RequestBody ProfileUpdateRequest request) {

        GkDraftProfilePreview existing = profileMapper.selectOne(
                new LambdaQueryWrapper<GkDraftProfilePreview>()
                        .eq(GkDraftProfilePreview::getDraftQuestionId, draftQuestionId)
                        .orderByDesc(GkDraftProfilePreview::getProfileVersion)
                        .last("LIMIT 1"));

        LocalDateTime now = LocalDateTime.now();
        if (existing != null) {
            existing.setKnowledgeTagsJson(request.getKnowledgeTagsJson());
            existing.setMethodTagsJson(request.getMethodTagsJson());
            existing.setFormulaTagsJson(request.getFormulaTagsJson());
            existing.setMistakeTagsJson(request.getMistakeTagsJson());
            existing.setAbilityTagsJson(request.getAbilityTagsJson());
            existing.setDifficultyScore(request.getDifficultyScore());
            existing.setDifficultyLevel(request.getDifficultyLevel());
            existing.setReasoningStepsJson(request.getReasoningStepsJson());
            existing.setAnalysisSummaryText(request.getAnalysisSummaryText());
            existing.setRecommendSeedText(request.getRecommendSeedText());
            existing.setUpdatedAt(now);
            profileMapper.updateById(existing);
            log.info("Updated profile preview for draftQuestionId={}, version={}", draftQuestionId, existing.getProfileVersion());
        } else {
            GkDraftProfilePreview profile = new GkDraftProfilePreview();
            profile.setDraftQuestionId(draftQuestionId);
            profile.setKnowledgeTagsJson(request.getKnowledgeTagsJson());
            profile.setMethodTagsJson(request.getMethodTagsJson());
            profile.setFormulaTagsJson(request.getFormulaTagsJson());
            profile.setMistakeTagsJson(request.getMistakeTagsJson());
            profile.setAbilityTagsJson(request.getAbilityTagsJson());
            profile.setDifficultyScore(request.getDifficultyScore());
            profile.setDifficultyLevel(request.getDifficultyLevel());
            profile.setReasoningStepsJson(request.getReasoningStepsJson());
            profile.setAnalysisSummaryText(request.getAnalysisSummaryText());
            profile.setRecommendSeedText(request.getRecommendSeedText());
            profile.setProfileVersion(1);
            profile.setConfirmed(false);
            profile.setCreatedAt(now);
            profile.setUpdatedAt(now);
            profileMapper.insert(profile);
            log.info("Created profile preview for draftQuestionId={}", draftQuestionId);
        }

        return ResponseEntity.ok().build();
    }

    @PutMapping("/papers/{paperId}/indexing")
    @Transactional
    public ResponseEntity<Void> updatePaperIndex(
            @PathVariable Long paperId,
            @RequestBody GaokaoIndexCallbackRequest request) {

        List<Long> ragQuestionIds = request.ragChunks() == null || request.ragChunks().isEmpty()
                ? List.of()
                : request.ragChunks().stream().map(GaokaoIndexCallbackRequest.RagChunkPayload::questionId).distinct().toList();
        if (!ragQuestionIds.isEmpty()) {
            ragChunkMapper.delete(new LambdaQueryWrapper<GkRagChunk>().in(GkRagChunk::getQuestionId, ragQuestionIds));
        }
        if (request.ragChunks() != null) {
            for (GaokaoIndexCallbackRequest.RagChunkPayload payload : request.ragChunks()) {
                GkRagChunk chunk = new GkRagChunk();
                chunk.setQuestionId(payload.questionId());
                chunk.setChunkUuid(payload.chunkUuid());
                chunk.setChunkType(payload.chunkType());
                chunk.setChunkText(payload.chunkText());
                chunk.setTokenCount(payload.tokenCount());
                chunk.setCreatedAt(LocalDateTime.now());
                ragChunkMapper.insert(chunk);
            }
        }

        List<Long> vectorTargetIds = request.vectorPoints() == null || request.vectorPoints().isEmpty()
                ? List.of()
                : request.vectorPoints().stream().map(GaokaoIndexCallbackRequest.VectorPointPayload::targetId).distinct().toList();
        List<String> vectorTargetTypes = request.vectorPoints() == null || request.vectorPoints().isEmpty()
                ? List.of()
                : request.vectorPoints().stream().map(GaokaoIndexCallbackRequest.VectorPointPayload::targetType).distinct().toList();
        if (!vectorTargetIds.isEmpty() && !vectorTargetTypes.isEmpty()) {
            vectorPointMapper.delete(new LambdaQueryWrapper<GkVectorPoint>()
                    .in(GkVectorPoint::getTargetId, vectorTargetIds)
                    .in(GkVectorPoint::getTargetType, vectorTargetTypes));
        }
        if (request.vectorPoints() != null) {
            for (GaokaoIndexCallbackRequest.VectorPointPayload payload : request.vectorPoints()) {
                GkVectorPoint point = new GkVectorPoint();
                point.setTargetType(payload.targetType());
                point.setTargetId(payload.targetId());
                point.setVectorKind(payload.vectorKind());
                point.setCollectionName(payload.collectionName());
                point.setQdrantPointId(payload.qdrantPointId());
                point.setPayloadJson(payload.payloadJson());
                point.setStatus(payload.status());
                point.setCreatedAt(LocalDateTime.now());
                point.setUpdatedAt(LocalDateTime.now());
                vectorPointMapper.insert(point);
            }
        }

        List<Long> edgeSourceQuestionIds = request.recommendEdges() == null || request.recommendEdges().isEmpty()
                ? List.of()
                : request.recommendEdges().stream().map(GaokaoIndexCallbackRequest.RecommendEdgePayload::sourceQuestionId).distinct().toList();
        if (!edgeSourceQuestionIds.isEmpty()) {
            recommendEdgeMapper.delete(new LambdaQueryWrapper<GkRecommendEdge>()
                    .in(GkRecommendEdge::getSourceQuestionId, edgeSourceQuestionIds));
        }
        if (request.recommendEdges() != null) {
            for (GaokaoIndexCallbackRequest.RecommendEdgePayload payload : request.recommendEdges()) {
                GkRecommendEdge edge = new GkRecommendEdge();
                edge.setSourceQuestionId(payload.sourceQuestionId());
                edge.setTargetQuestionId(payload.targetQuestionId());
                edge.setRelationType(payload.relationType());
                edge.setScore(payload.score());
                edge.setComputedAt(LocalDateTime.now());
                recommendEdgeMapper.insert(edge);
            }
        }

        GkPaper paper = paperMapper.selectById(paperId);
        if (paper != null) {
            paper.setStatus(request.status());
            paper.setUpdatedAt(LocalDateTime.now());
            paperMapper.updateById(paper);
        }

        log.info("Updated gaokao index callback for paperId={}, status={}", paperId, request.status());
        return ResponseEntity.ok().build();
    }
}
