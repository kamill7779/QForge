package io.github.kamill7779.qforge.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "qforge.cache")
public class QForgeCacheProperties {

    private int basketTtlSeconds = 600;

    public int getBasketTtlSeconds() {
        return basketTtlSeconds;
    }

    public void setBasketTtlSeconds(int basketTtlSeconds) {
        this.basketTtlSeconds = basketTtlSeconds;
    }
}
