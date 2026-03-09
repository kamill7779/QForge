package io.github.kamill7779.qforge.gaokaoanalysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class BuildVectorRequest {

    @NotNull
    private Long questionId;

    @NotBlank
    private String questionUuid;

    private String stemText;
    private String normalizedStemText;
    private String analysisSummaryText;

    private List<String> knowledgeCodes;
    private List<String> methodCodes;

    private String questionTypeCode;
    private String difficultyLevel;
    private String examYear;
    private String provinceCode;

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

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

    public String getNormalizedStemText() {
        return normalizedStemText;
    }

    public void setNormalizedStemText(String normalizedStemText) {
        this.normalizedStemText = normalizedStemText;
    }

    public String getAnalysisSummaryText() {
        return analysisSummaryText;
    }

    public void setAnalysisSummaryText(String analysisSummaryText) {
        this.analysisSummaryText = analysisSummaryText;
    }

    public List<String> getKnowledgeCodes() {
        return knowledgeCodes;
    }

    public void setKnowledgeCodes(List<String> knowledgeCodes) {
        this.knowledgeCodes = knowledgeCodes;
    }

    public List<String> getMethodCodes() {
        return methodCodes;
    }

    public void setMethodCodes(List<String> methodCodes) {
        this.methodCodes = methodCodes;
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

    public String getExamYear() {
        return examYear;
    }

    public void setExamYear(String examYear) {
        this.examYear = examYear;
    }

    public String getProvinceCode() {
        return provinceCode;
    }

    public void setProvinceCode(String provinceCode) {
        this.provinceCode = provinceCode;
    }
}
