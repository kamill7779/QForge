package io.github.kamill7779.qforge.gaokaoanalysis.dto;

import java.math.BigDecimal;

public class RecommendedQuestionDTO {

    private String questionUuid;
    private String stemText;
    private String questionTypeCode;
    private String difficultyLevel;
    private BigDecimal score;

    public String getQuestionUuid() {
        return questionUuid;
    }

    public void setQuestionUuid(String questionUuid) {
        this.questionUuid = questionUuid;
    }

    public String getStemText() {
        return stemText;
    }

    public void setStemText(String stemText) {
        this.stemText = stemText;
    }

    public String getQuestionTypeCode() {
        return questionTypeCode;
    }

    public void setQuestionTypeCode(String questionTypeCode) {
        this.questionTypeCode = questionTypeCode;
    }

    public String getDifficultyLevel() {
        return difficultyLevel;
    }

    public void setDifficultyLevel(String difficultyLevel) {
        this.difficultyLevel = difficultyLevel;
    }

    public BigDecimal getScore() {
        return score;
    }

    public void setScore(BigDecimal score) {
        this.score = score;
    }
}
