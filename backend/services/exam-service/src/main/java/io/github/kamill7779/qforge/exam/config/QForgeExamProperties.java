package io.github.kamill7779.qforge.exam.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qforge.exam")
public class QForgeExamProperties {

    private int defaultDurationMinutes = 120;
    private BigDecimal defaultQuestionScore = new BigDecimal("5.0");

    public int getDefaultDurationMinutes() {
        return defaultDurationMinutes;
    }

    public void setDefaultDurationMinutes(int defaultDurationMinutes) {
        this.defaultDurationMinutes = defaultDurationMinutes;
    }

    public BigDecimal getDefaultQuestionScore() {
        return defaultQuestionScore;
    }

    public void setDefaultQuestionScore(BigDecimal defaultQuestionScore) {
        this.defaultQuestionScore = defaultQuestionScore;
    }
}
