package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;
import java.util.List;

public class PhotoQueryResponse {

    private List<MatchResult> results;

    public List<MatchResult> getResults() { return results; }
    public void setResults(List<MatchResult> results) { this.results = results; }

    public static class MatchResult {
        private String questionUuid;
        private String stemText;
        private BigDecimal similarity;

        public String getQuestionUuid() { return questionUuid; }
        public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

        public String getStemText() { return stemText; }
        public void setStemText(String stemText) { this.stemText = stemText; }

        public BigDecimal getSimilarity() { return similarity; }
        public void setSimilarity(BigDecimal similarity) { this.similarity = similarity; }
    }
}
