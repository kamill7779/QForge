package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;
import java.util.List;

public class PhotoQueryResponse {

    private QueryQuestion queryQuestion;
    private String ocrRaw;
    private AnalysisProfile analysisProfile;
    private List<RecommendGroup> recommendGroups;
    private String reasonSummary;
    private List<MatchResult> results;

    public QueryQuestion getQueryQuestion() { return queryQuestion; }
    public void setQueryQuestion(QueryQuestion queryQuestion) { this.queryQuestion = queryQuestion; }

    public String getOcrRaw() { return ocrRaw; }
    public void setOcrRaw(String ocrRaw) { this.ocrRaw = ocrRaw; }

    public AnalysisProfile getAnalysisProfile() { return analysisProfile; }
    public void setAnalysisProfile(AnalysisProfile analysisProfile) { this.analysisProfile = analysisProfile; }

    public List<RecommendGroup> getRecommendGroups() { return recommendGroups; }
    public void setRecommendGroups(List<RecommendGroup> recommendGroups) { this.recommendGroups = recommendGroups; }

    public String getReasonSummary() { return reasonSummary; }
    public void setReasonSummary(String reasonSummary) { this.reasonSummary = reasonSummary; }

    public List<MatchResult> getResults() { return results; }
    public void setResults(List<MatchResult> results) { this.results = results; }

    public static class QueryQuestion {
        private String stemText;
        private String stemXml;
        private String answerXml;

        public String getStemText() { return stemText; }
        public void setStemText(String stemText) { this.stemText = stemText; }

        public String getStemXml() { return stemXml; }
        public void setStemXml(String stemXml) { this.stemXml = stemXml; }

        public String getAnswerXml() { return answerXml; }
        public void setAnswerXml(String answerXml) { this.answerXml = answerXml; }
    }

    public static class AnalysisProfile {
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
    }

    public static class RecommendGroup {
        private String relationType;
        private List<MatchResult> questions;

        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }

        public List<MatchResult> getQuestions() { return questions; }
        public void setQuestions(List<MatchResult> questions) { this.questions = questions; }
    }

    public static class MatchResult {
        private String questionUuid;
        private String stemText;
        private String questionTypeCode;
        private String difficultyLevel;
        private BigDecimal similarity;

        public String getQuestionUuid() { return questionUuid; }
        public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

        public String getStemText() { return stemText; }
        public void setStemText(String stemText) { this.stemText = stemText; }

        public String getQuestionTypeCode() { return questionTypeCode; }
        public void setQuestionTypeCode(String questionTypeCode) { this.questionTypeCode = questionTypeCode; }

        public String getDifficultyLevel() { return difficultyLevel; }
        public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

        public BigDecimal getSimilarity() { return similarity; }
        public void setSimilarity(BigDecimal similarity) { this.similarity = similarity; }
    }
}
