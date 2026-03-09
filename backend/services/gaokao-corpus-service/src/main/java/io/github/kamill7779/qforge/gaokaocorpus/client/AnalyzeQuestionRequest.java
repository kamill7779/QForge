package io.github.kamill7779.qforge.gaokaocorpus.client;

public class AnalyzeQuestionRequest {

    private Long draftQuestionId;
    private String stemText;
    private String stemXml;
    private String answerText;
    private String questionTypeCode;

    public Long getDraftQuestionId() { return draftQuestionId; }
    public void setDraftQuestionId(Long draftQuestionId) { this.draftQuestionId = draftQuestionId; }

    public String getStemText() { return stemText; }
    public void setStemText(String stemText) { this.stemText = stemText; }

    public String getStemXml() { return stemXml; }
    public void setStemXml(String stemXml) { this.stemXml = stemXml; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public String getQuestionTypeCode() { return questionTypeCode; }
    public void setQuestionTypeCode(String questionTypeCode) { this.questionTypeCode = questionTypeCode; }
}
