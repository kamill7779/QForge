package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;

public class UpdateDraftQuestionRequest {

    private String questionNo;
    private String questionTypeCode;
    private String answerMode;
    private String stemText;
    private String stemXml;
    private BigDecimal score;
    private Integer editVersion;

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
}
