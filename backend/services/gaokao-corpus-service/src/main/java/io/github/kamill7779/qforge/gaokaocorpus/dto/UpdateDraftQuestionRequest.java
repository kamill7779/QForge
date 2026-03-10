package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;
import java.util.List;

public class UpdateDraftQuestionRequest {

    private String draftSectionUuid;
    private String parentDraftQuestionUuid;
    private String questionNo;
    private String questionTypeCode;
    private String answerMode;
    private String stemText;
    private String stemXml;
    private BigDecimal score;
    private Integer editVersion;
    private List<OptionPayload> options;
    private List<AnswerPayload> answers;
    private List<AssetPayload> stemAssets;

    public String getDraftSectionUuid() { return draftSectionUuid; }
    public void setDraftSectionUuid(String draftSectionUuid) { this.draftSectionUuid = draftSectionUuid; }

    public String getParentDraftQuestionUuid() { return parentDraftQuestionUuid; }
    public void setParentDraftQuestionUuid(String parentDraftQuestionUuid) { this.parentDraftQuestionUuid = parentDraftQuestionUuid; }

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

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public Integer getEditVersion() { return editVersion; }
    public void setEditVersion(Integer editVersion) { this.editVersion = editVersion; }

    public List<OptionPayload> getOptions() { return options; }
    public void setOptions(List<OptionPayload> options) { this.options = options; }

    public List<AnswerPayload> getAnswers() { return answers; }
    public void setAnswers(List<AnswerPayload> answers) { this.answers = answers; }

    public List<AssetPayload> getStemAssets() { return stemAssets; }
    public void setStemAssets(List<AssetPayload> stemAssets) { this.stemAssets = stemAssets; }

    public static class OptionPayload {
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

    public static class AnswerPayload {
        private String draftAnswerUuid;
        private String answerType;
        private String answerText;
        private String answerXml;
        private Boolean official;
        private Integer sortOrder;
        private List<AssetPayload> assets;

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

        public List<AssetPayload> getAssets() { return assets; }
        public void setAssets(List<AssetPayload> assets) { this.assets = assets; }
    }

    public static class AssetPayload {
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
}
