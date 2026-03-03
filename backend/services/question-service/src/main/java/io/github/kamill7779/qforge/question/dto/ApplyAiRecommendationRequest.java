package io.github.kamill7779.qforge.question.dto;

import java.math.BigDecimal;
import java.util.List;

public class ApplyAiRecommendationRequest {

    private List<String> tags;
    private BigDecimal difficulty;

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public BigDecimal getDifficulty() { return difficulty; }
    public void setDifficulty(BigDecimal difficulty) { this.difficulty = difficulty; }
}
