package io.github.kamill7779.qforge.ocr.config;

import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@EnableConfigurationProperties(OcrProviderProperties.class)
public class OcrClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder, OcrProviderProperties properties) {
        if (properties.isPreferIpv4()) {
            System.setProperty("java.net.preferIPv4Stack", "true");
        }
        return builder
                .setConnectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .build();
    }
}
