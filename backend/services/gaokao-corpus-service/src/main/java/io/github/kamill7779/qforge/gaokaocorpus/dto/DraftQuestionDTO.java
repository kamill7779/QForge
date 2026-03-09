package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;

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
}
