package io.github.kamill7779.qforge.question.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qforge.cache")
public class QForgeCacheProperties {

    private int tagCatalogTtlSeconds = 21600;
    private int questionSummaryTtlSeconds = 600;

    public int getTagCatalogTtlSeconds() {
        return tagCatalogTtlSeconds;
    }

    public void setTagCatalogTtlSeconds(int tagCatalogTtlSeconds) {
        this.tagCatalogTtlSeconds = tagCatalogTtlSeconds;
    }

    public int getQuestionSummaryTtlSeconds() {
        return questionSummaryTtlSeconds;
    }

    public void setQuestionSummaryTtlSeconds(int questionSummaryTtlSeconds) {
        this.questionSummaryTtlSeconds = questionSummaryTtlSeconds;
    }
}
