package io.github.kamill7779.qforge.ai.config;

import ai.z.openapi.ZhipuAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 初始化 ZhipuAI 官方 SDK 客户端 Bean
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ZhipuProperties.class)
public class ZhipuAiConfig {

    private final ZhipuProperties props;

    @Bean
    public ZhipuAiClient zhipuAiClient() {
        log.info("Initializing ZhipuAiClient, model={}", props.getModel());
        return ZhipuAiClient.builder()
                .ofZHIPU()
                .apiKey(props.getApiKey())
                .build();
    }
}
