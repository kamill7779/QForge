package io.github.kamill7779.qforge.gaokaocorpus.client;

import java.math.BigDecimal;
import java.util.List;

public class PhotoQueryInternalResponse {

    private String stemText;
    private String stemXml;
    private String reasonSummary;
    private List<RecommendGroup> recommendGroups;

    public String getStemText() { return stemText; }
    public void setStemText(String stemText) { this.stemText = stemText; }

    public String getStemXml() { return stemXml; }
    public void setStemXml(String stemXml) { this.stemXml = stemXml; }

    public String getReasonSummary() { return reasonSummary; }
    public void setReasonSummary(String reasonSummary) { this.reasonSummary = reasonSummary; }

    public List<RecommendGroup> getRecommendGroups() { return recommendGroups; }
    public void setRecommendGroups(List<RecommendGroup> recommendGroups) { this.recommendGroups = recommendGroups; }

    public static class RecommendGroup {
        private String relationType;
        private List<RecommendedQuestion> questions;

        public String getRelationType() { return relationType; }
        public void setRelationType(String relationType) { this.relationType = relationType; }

        public List<RecommendedQuestion> getQuestions() { return questions; }
        public void setQuestions(List<RecommendedQuestion> questions) { this.questions = questions; }
    }

    public static class RecommendedQuestion {
        private String questionUuid;
        private String stemText;
        private BigDecimal score;

        public String getQuestionUuid() { return questionUuid; }
        public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

        public String getStemText() { return stemText; }
        public void setStemText(String stemText) { this.stemText = stemText; }

        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }
    }
}
