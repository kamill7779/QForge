package io.github.kamill7779.qforge.internal.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 从高考数学语料库物化到 question-core-service 正式题库的请求。
 * 由 gaokao-corpus-service 调用 QuestionCoreClient.createFromGaokao()。
 */
public class CreateQuestionFromGaokaoRequest {

    @NotBlank
    private String ownerUser;
    private String questionTypeCode;
    @NotBlank
    private String stemText;
    private String stemXml;
    private String answerText;
    private String answerXml;
    private String source;
    private String difficultyLevel;
    private java.math.BigDecimal difficulty;
    private java.util.List<TagEntry> mainTags;
    private java.util.List<String> secondaryTags;
    private java.util.List<AssetEntry> stemAssets;
    private java.util.List<AssetEntry> answerAssets;

    // --- Nested types ---

    public static class TagEntry {
        private String categoryCode;
        private String tagCode;

        public String getCategoryCode() { return categoryCode; }
        public void setCategoryCode(String categoryCode) { this.categoryCode = categoryCode; }
        public String getTagCode() { return tagCode; }
        public void setTagCode(String tagCode) { this.tagCode = tagCode; }
    }

    public static class AssetEntry {
        private String assetType;
        private String storageRef;
        private String refKey;

        public String getAssetType() { return assetType; }
        public void setAssetType(String assetType) { this.assetType = assetType; }
        public String getStorageRef() { return storageRef; }
        public void setStorageRef(String storageRef) { this.storageRef = storageRef; }
        public String getRefKey() { return refKey; }
        public void setRefKey(String refKey) { this.refKey = refKey; }
    }

    // --- Getters and setters ---

    public String getOwnerUser() { return ownerUser; }
    public void setOwnerUser(String ownerUser) { this.ownerUser = ownerUser; }

    public String getQuestionTypeCode() { return questionTypeCode; }
    public void setQuestionTypeCode(String questionTypeCode) { this.questionTypeCode = questionTypeCode; }

    public String getStemText() { return stemText; }
    public void setStemText(String stemText) { this.stemText = stemText; }

    public String getStemXml() { return stemXml; }
    public void setStemXml(String stemXml) { this.stemXml = stemXml; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public String getAnswerXml() { return answerXml; }
    public void setAnswerXml(String answerXml) { this.answerXml = answerXml; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public java.math.BigDecimal getDifficulty() { return difficulty; }
    public void setDifficulty(java.math.BigDecimal difficulty) { this.difficulty = difficulty; }

    public java.util.List<TagEntry> getMainTags() { return mainTags; }
    public void setMainTags(java.util.List<TagEntry> mainTags) { this.mainTags = mainTags; }

    public java.util.List<String> getSecondaryTags() { return secondaryTags; }
    public void setSecondaryTags(java.util.List<String> secondaryTags) { this.secondaryTags = secondaryTags; }

    public java.util.List<AssetEntry> getStemAssets() { return stemAssets; }
    public void setStemAssets(java.util.List<AssetEntry> stemAssets) { this.stemAssets = stemAssets; }

    public java.util.List<AssetEntry> getAnswerAssets() { return answerAssets; }
    public void setAnswerAssets(java.util.List<AssetEntry> answerAssets) { this.answerAssets = answerAssets; }
}
