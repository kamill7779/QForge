package io.github.kamill7779.qforge.ocr.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.ocr.client.AnswerXmlConverter;
import io.github.kamill7779.qforge.ocr.client.ExamSplitLlmClient;
import io.github.kamill7779.qforge.ocr.client.StemXmlConverter;
import io.github.kamill7779.qforge.ocr.mq.AiAnalysisTaskConsumer;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

class ZhipuAiConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "zhipuai.api-key=",
                    "zhipuai.model=glm-5",
                    "stemxml.model=glm-4-0520",
                    "answerxml.model=glm-4-0520",
                    "examparse.ai.model=glm-4-plus"
            );

    @Test
    void shouldAllowContextStartupWhenZhipuApiKeyIsBlank() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(AnswerXmlConverter.class);
            assertThat(context).hasSingleBean(StemXmlConverter.class);
            assertThat(context).hasSingleBean(ExamSplitLlmClient.class);
            assertThat(context).hasSingleBean(AiAnalysisTaskConsumer.class);
        });
    }

    @Test
    void shouldFailLazilyWithClearMessageWhenZhipuFeatureIsInvokedWithoutApiKey() {
        contextRunner.run(context -> {
            AnswerXmlConverter converter = context.getBean(AnswerXmlConverter.class);

            assertThatThrownBy(() -> converter.convertToAnswerXml("sample OCR text"))
                    .hasMessageContaining("ZHIPUAI_API_KEY is blank");
        });
    }

    @Import({
            ZhipuAiConfig.class,
            QForgeOcrProperties.class,
            AnswerXmlConverter.class,
            StemXmlConverter.class,
            ExamSplitLlmClient.class,
            AiAnalysisTaskConsumer.class
    })
    static class TestConfig {

        @Bean
        RabbitTemplate rabbitTemplate() {
            return mock(RabbitTemplate.class);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
