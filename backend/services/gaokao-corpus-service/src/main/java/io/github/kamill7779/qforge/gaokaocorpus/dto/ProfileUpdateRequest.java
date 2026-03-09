package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;

public class ProfileUpdateRequest {

    private String knowledgeTagsJson;
    private String methodTagsJson;
    private String formulaTagsJson;
    private String mistakeTagsJson;
    private String abilityTagsJson;
    private BigDecimal difficultyScore;
    private String difficultyLevel;
    private String reasoningStepsJson;
    private String analysisSummaryText;
    private String recommendSeedText;
    private String stemXml;
    private String answerXml;

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

    public String getStemXml() { return stemXml; }
    public void setStemXml(String stemXml) { this.stemXml = stemXml; }

    public String getAnswerXml() { return answerXml; }
    public void setAnswerXml(String answerXml) { this.answerXml = answerXml; }
}
