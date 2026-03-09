package io.github.kamill7779.qforge.gaokaocorpus.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.kamill7779.qforge.gaokaocorpus.dto.ProfileUpdateRequest;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftProfilePreview;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftProfilePreviewMapper;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
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

    public InternalCorpusController(GkDraftProfilePreviewMapper profileMapper) {
        this.profileMapper = profileMapper;
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
}
