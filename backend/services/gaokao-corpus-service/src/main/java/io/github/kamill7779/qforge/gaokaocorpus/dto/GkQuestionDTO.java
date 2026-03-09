package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;

public class GkQuestionDTO {

    private String questionUuid;
    private String paperUuid;
    private String questionNo;
    private String questionTypeCode;
    private String answerMode;
    private String stemText;
    private String stemXml;
    private String normalizedStemText;
    private BigDecimal score;
    private BigDecimal difficultyScore;
    private String difficultyLevel;
    private Integer reasoningStepCount;
    private Boolean hasAnswer;

    public String getQuestionUuid() { return questionUuid; }
    public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

    public String getPaperUuid() { return paperUuid; }
    public void setPaperUuid(String paperUuid) { this.paperUuid = paperUuid; }

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

    public BigDecimal getDifficultyScore() { return difficultyScore; }
    public void setDifficultyScore(BigDecimal difficultyScore) { this.difficultyScore = difficultyScore; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public Integer getReasoningStepCount() { return reasoningStepCount; }
    public void setReasoningStepCount(Integer reasoningStepCount) { this.reasoningStepCount = reasoningStepCount; }

    public Boolean getHasAnswer() { return hasAnswer; }
    public void setHasAnswer(Boolean hasAnswer) { this.hasAnswer = hasAnswer; }
}
