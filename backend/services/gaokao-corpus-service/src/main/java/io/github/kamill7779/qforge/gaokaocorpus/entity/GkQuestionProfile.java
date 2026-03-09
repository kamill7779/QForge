package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gk_question_profile")
public class GkQuestionProfile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("question_id")
    private Long questionId;

    @TableField("knowledge_path_json")
    private String knowledgePathJson;

    @TableField("method_tags_json")
    private String methodTagsJson;

    @TableField("ability_tags_json")
    private String abilityTagsJson;

    @TableField("mistake_tags_json")
    private String mistakeTagsJson;

    @TableField("formula_tags_json")
    private String formulaTagsJson;

    @TableField("answer_structure_json")
    private String answerStructureJson;

    @TableField("reasoning_steps_json")
    private String reasoningStepsJson;

    @TableField("analysis_summary_text")
    private String analysisSummaryText;

    @TableField("solve_path_text")
    private String solvePathText;

    @TableField("difficulty_score")
    private BigDecimal difficultyScore;

    @TableField("difficulty_level")
    private String difficultyLevel;

    @TableField("profile_version")
    private Integer profileVersion;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getKnowledgePathJson() { return knowledgePathJson; }
    public void setKnowledgePathJson(String knowledgePathJson) { this.knowledgePathJson = knowledgePathJson; }

    public String getMethodTagsJson() { return methodTagsJson; }
    public void setMethodTagsJson(String methodTagsJson) { this.methodTagsJson = methodTagsJson; }

    public String getAbilityTagsJson() { return abilityTagsJson; }
    public void setAbilityTagsJson(String abilityTagsJson) { this.abilityTagsJson = abilityTagsJson; }

    public String getMistakeTagsJson() { return mistakeTagsJson; }
    public void setMistakeTagsJson(String mistakeTagsJson) { this.mistakeTagsJson = mistakeTagsJson; }

    public String getFormulaTagsJson() { return formulaTagsJson; }
    public void setFormulaTagsJson(String formulaTagsJson) { this.formulaTagsJson = formulaTagsJson; }

    public String getAnswerStructureJson() { return answerStructureJson; }
    public void setAnswerStructureJson(String answerStructureJson) { this.answerStructureJson = answerStructureJson; }

    public String getReasoningStepsJson() { return reasoningStepsJson; }
    public void setReasoningStepsJson(String reasoningStepsJson) { this.reasoningStepsJson = reasoningStepsJson; }

    public String getAnalysisSummaryText() { return analysisSummaryText; }
    public void setAnalysisSummaryText(String analysisSummaryText) { this.analysisSummaryText = analysisSummaryText; }

    public String getSolvePathText() { return solvePathText; }
    public void setSolvePathText(String solvePathText) { this.solvePathText = solvePathText; }

    public BigDecimal getDifficultyScore() { return difficultyScore; }
    public void setDifficultyScore(BigDecimal difficultyScore) { this.difficultyScore = difficultyScore; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public Integer getProfileVersion() { return profileVersion; }
    public void setProfileVersion(Integer profileVersion) { this.profileVersion = profileVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
