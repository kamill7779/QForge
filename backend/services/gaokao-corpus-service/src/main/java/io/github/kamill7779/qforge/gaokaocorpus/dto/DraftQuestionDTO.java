package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;
import java.util.List;

public class DraftQuestionDTO {

    private String draftQuestionUuid;
    private String questionNo;
    private String questionTypeCode;
    private String answerMode;
    private String stemText;
    private String stemXml;
    private String normalizedStemText;
    private BigDecimal score;
    private Boolean hasAnswer;
    private Integer editVersion;
    private List<DraftOptionDTO> options;
    private List<DraftAnswerDTO> answers;
    private List<DraftAssetDTO> stemAssets;
    private AnalysisPreviewDTO analysisPreview;
    private List<DraftQuestionDTO> childQuestions;

    public String getDraftQuestionUuid() { return draftQuestionUuid; }
    public void setDraftQuestionUuid(String draftQuestionUuid) { this.draftQuestionUuid = draftQuestionUuid; }

    public String getQuestionNo() { return questionNo; }
    public void setQuestionNo(String questionNo) { this.questionNo = questionNo; }

    public String getQuestionTypeCode() { return questionTypeCode; }
    public void setQuestionTypeCode(String questionTypeCode) { this.questionTypeCode = questionTypeCode; }

    public String getAnswerMode() { return answerMode; }
    public void setAnswerMode(String answerMode) { this.answerMode = answerMode; }

    public String getStemText() { return stemText; }
    public void setStemText(String stemText) { this.stemText = stemText; }

    public String getStemXml() { return stemXml; }
    public void setStemXml(String stemXml) { this.stemXml = stemXml; }

    public String getNormalizedStemText() { return normalizedStemText; }
    public void setNormalizedStemText(String normalizedStemText) { this.normalizedStemText = normalizedStemText; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public Boolean getHasAnswer() { return hasAnswer; }
    public void setHasAnswer(Boolean hasAnswer) { this.hasAnswer = hasAnswer; }

    public Integer getEditVersion() { return editVersion; }
    public void setEditVersion(Integer editVersion) { this.editVersion = editVersion; }

    public List<DraftOptionDTO> getOptions() { return options; }
    public void setOptions(List<DraftOptionDTO> options) { this.options = options; }

    public List<DraftAnswerDTO> getAnswers() { return answers; }
    public void setAnswers(List<DraftAnswerDTO> answers) { this.answers = answers; }

    public List<DraftAssetDTO> getStemAssets() { return stemAssets; }
    public void setStemAssets(List<DraftAssetDTO> stemAssets) { this.stemAssets = stemAssets; }

    public AnalysisPreviewDTO getAnalysisPreview() { return analysisPreview; }
    public void setAnalysisPreview(AnalysisPreviewDTO analysisPreview) { this.analysisPreview = analysisPreview; }

    public List<DraftQuestionDTO> getChildQuestions() { return childQuestions; }
    public void setChildQuestions(List<DraftQuestionDTO> childQuestions) { this.childQuestions = childQuestions; }

    public static class DraftOptionDTO {
        private String draftOptionUuid;
        private String optionLabel;
        private String optionText;
        private String optionXml;
        private Integer sortOrder;

        public String getDraftOptionUuid() { return draftOptionUuid; }
        public void setDraftOptionUuid(String draftOptionUuid) { this.draftOptionUuid = draftOptionUuid; }

        public String getOptionLabel() { return optionLabel; }
        public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }

        public String getOptionText() { return optionText; }
        public void setOptionText(String optionText) { this.optionText = optionText; }

        public String getOptionXml() { return optionXml; }
        public void setOptionXml(String optionXml) { this.optionXml = optionXml; }

        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class DraftAnswerDTO {
        private String draftAnswerUuid;
        private String answerType;
        private String answerText;
        private String answerXml;
        private Boolean official;
        private Integer sortOrder;
        private List<DraftAssetDTO> assets;

        public String getDraftAnswerUuid() { return draftAnswerUuid; }
        public void setDraftAnswerUuid(String draftAnswerUuid) { this.draftAnswerUuid = draftAnswerUuid; }

        public String getAnswerType() { return answerType; }
        public void setAnswerType(String answerType) { this.answerType = answerType; }

        public String getAnswerText() { return answerText; }
        public void setAnswerText(String answerText) { this.answerText = answerText; }

        public String getAnswerXml() { return answerXml; }
        public void setAnswerXml(String answerXml) { this.answerXml = answerXml; }

        public Boolean getOfficial() { return official; }
        public void setOfficial(Boolean official) { this.official = official; }

        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

        public List<DraftAssetDTO> getAssets() { return assets; }
        public void setAssets(List<DraftAssetDTO> assets) { this.assets = assets; }
    }

    public static class DraftAssetDTO {
        private String assetType;
        private String storageRef;
        private Integer sortOrder;

        public String getAssetType() { return assetType; }
        public void setAssetType(String assetType) { this.assetType = assetType; }

        public String getStorageRef() { return storageRef; }
        public void setStorageRef(String storageRef) { this.storageRef = storageRef; }

        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    }

    public static class AnalysisPreviewDTO {
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
        private Integer profileVersion;
        private Boolean confirmed;

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
    }
}
