package io.github.kamill7779.qforge.gaokaoanalysis.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qforge.analysis")
public class QForgeAnalysisProperties {

    private String aiModel;
    private int aiTimeoutSeconds;
    private String embeddingModel;
    private int embeddingDimension;
    private int maxSimilarResults;
    private int rerankTopK;

    public String getAiModel() {
        return aiModel;
    }

    public void setAiModel(String aiModel) {
        this.aiModel = aiModel;
    }

    public int getAiTimeoutSeconds() {
        return aiTimeoutSeconds;
    }

    public void setAiTimeoutSeconds(int aiTimeoutSeconds) {
        this.aiTimeoutSeconds = aiTimeoutSeconds;
    }

    public String getEmbeddingModel() {
        return embeddingModel;
    }

    public void setEmbeddingModel(String embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public int getMaxSimilarResults() {
        return maxSimilarResults;
    }

    public void setMaxSimilarResults(int maxSimilarResults) {
        this.maxSimilarResults = maxSimilarResults;
    }

    public int getRerankTopK() {
        return rerankTopK;
    }

    public void setRerankTopK(int rerankTopK) {
        this.rerankTopK = rerankTopK;
    }
}
