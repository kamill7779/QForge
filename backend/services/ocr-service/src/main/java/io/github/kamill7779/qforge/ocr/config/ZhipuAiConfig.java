package io.github.kamill7779.qforge.ocr.config;

import ai.z.openapi.ZhipuAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

/**
 * 初始化 ZhipuAI 官方 SDK 客户端，用于 OCR 后处理（文本转 XML）。
 */
@Configuration
@EnableConfigurationProperties({
        ZhipuAiProperties.class,
        StemXmlProperties.class,
        AnswerXmlProperties.class,
        ExamParseAiProperties.class
})
public class ZhipuAiConfig {

    private static final Logger log = LoggerFactory.getLogger(ZhipuAiConfig.class);

    @Bean
    @Lazy
    public ZhipuAiClient zhipuAiClient(ZhipuAiProperties props) {
        if (props.getApiKey() == null || props.getApiKey().isBlank()) {
            throw new IllegalStateException(
                    "ZHIPUAI_API_KEY is blank; Zhipu AI features are disabled until the key is configured");
        }
        log.info("Initializing ZhipuAiClient for OCR post-processing, model={}", props.getModel());
        return ZhipuAiClient.builder()
                .ofZHIPU()
                .apiKey(props.getApiKey())
                .build();
    }
}
