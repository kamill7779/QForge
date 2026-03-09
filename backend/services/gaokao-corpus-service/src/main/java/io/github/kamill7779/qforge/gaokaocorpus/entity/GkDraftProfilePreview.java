package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gk_draft_profile_preview")
public class GkDraftProfilePreview {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("draft_question_id")
    private Long draftQuestionId;

    @TableField("knowledge_tags_json")
    private String knowledgeTagsJson;

    @TableField("method_tags_json")
    private String methodTagsJson;

    @TableField("formula_tags_json")
    private String formulaTagsJson;

    @TableField("mistake_tags_json")
    private String mistakeTagsJson;

    @TableField("ability_tags_json")
    private String abilityTagsJson;

    @TableField("difficulty_score")
    private BigDecimal difficultyScore;

    @TableField("difficulty_level")
    private String difficultyLevel;

    @TableField("reasoning_steps_json")
    private String reasoningStepsJson;

    @TableField("analysis_summary_text")
    private String analysisSummaryText;

    @TableField("recommend_seed_text")
    private String recommendSeedText;

    @TableField("profile_version")
    private Integer profileVersion;

    private Boolean confirmed;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDraftQuestionId() { return draftQuestionId; }
    public void setDraftQuestionId(Long draftQuestionId) { this.draftQuestionId = draftQuestionId; }

    public String getKnowledgeTagsJson() { return knowledgeTagsJson; }
    public void setKnowledgeTagsJson(String knowledgeTagsJson) { this.knowledgeTagsJson = knowledgeTagsJson; }

    public String getMethodTagsJson() { return methodTagsJson; }
    public void setMethodTagsJson(String methodTagsJson) { this.methodTagsJson = methodTagsJson; }

    public String getFormulaTagsJson() { return formulaTagsJson; }
    public void setFormulaTagsJson(String formulaTagsJson) { this.formulaTagsJson = formulaTagsJson; }

    public String getMistakeTagsJson() { return mistakeTagsJson; }
    public void setMistakeTagsJson(String mistakeTagsJson) { this.mistakeTagsJson = mistakeTagsJson; }

    public String getAbilityTagsJson() { return abilityTagsJson; }
    public void setAbilityTagsJson(String abilityTagsJson) { this.abilityTagsJson = abilityTagsJson; }

    public BigDecimal getDifficultyScore() { return difficultyScore; }
    public void setDifficultyScore(BigDecimal difficultyScore) { this.difficultyScore = difficultyScore; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public String getReasoningStepsJson() { return reasoningStepsJson; }
    public void setReasoningStepsJson(String reasoningStepsJson) { this.reasoningStepsJson = reasoningStepsJson; }

    public String getAnalysisSummaryText() { return analysisSummaryText; }
    public void setAnalysisSummaryText(String analysisSummaryText) { this.analysisSummaryText = analysisSummaryText; }

    public String getRecommendSeedText() { return recommendSeedText; }
    public void setRecommendSeedText(String recommendSeedText) { this.recommendSeedText = recommendSeedText; }

    public Integer getProfileVersion() { return profileVersion; }
    public void setProfileVersion(Integer profileVersion) { this.profileVersion = profileVersion; }

    public Boolean getConfirmed() { return confirmed; }
    public void setConfirmed(Boolean confirmed) { this.confirmed = confirmed; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
