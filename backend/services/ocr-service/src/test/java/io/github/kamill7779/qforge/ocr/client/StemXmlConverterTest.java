package io.github.kamill7779.qforge.ocr.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Answers.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ai.z.openapi.ZhipuAiClient;
import ai.z.openapi.service.model.ChatCompletionCreateParams;
import ai.z.openapi.service.model.ChatCompletionResponse;
import io.github.kamill7779.qforge.ocr.config.ZhipuAiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StemXmlConverterTest {

    private ZhipuAiClient zhipuAiClient;
    private ZhipuAiProperties properties;
    private StemXmlConverter converter;

    @BeforeEach
    void setUp() {
        zhipuAiClient = mock(ZhipuAiClient.class, RETURNS_DEEP_STUBS);

        properties = new ZhipuAiProperties();
        properties.setApiKey("test-key");
        properties.setModel("glm-5");
        properties.setTemperature(0.1f);
        properties.setMaxTokens(2048);

        converter = new StemXmlConverter(zhipuAiClient, properties);
    }

    @Test
    void shouldReturnStemXmlWhenGlmSucceeds() {
        String expectedXml = "<stem version=\"1\">\n  <p>题目</p>\n</stem>";
        mockSuccessResponse(expectedXml);

        String result = converter.convertToStemXml("OCR原始文本");

        assertEquals(expectedXml, result);
    }

    @Test
    void shouldStripCodeFencesFromResponse() {
        String wrappedXml = "```xml\n<stem version=\"1\">\n  <p>题目</p>\n</stem>\n```";
        mockSuccessResponse(wrappedXml);

        String result = converter.convertToStemXml("OCR原始文本");

        assertEquals("<stem version=\"1\">\n  <p>题目</p>\n</stem>", result);
    }

    @Test
    void shouldReturnNullForNullInput() {
        assertNull(converter.convertToStemXml(null));
    }

    @Test
    void shouldReturnBlankInputAsIs() {
        assertEquals("", converter.convertToStemXml(""));
        assertEquals("   ", converter.convertToStemXml("   "));
    }

    @Test
    void shouldThrowWhenGlmFails() {
        ChatCompletionResponse failedResponse = mock(ChatCompletionResponse.class);
        when(failedResponse.isSuccess()).thenReturn(false);
        when(failedResponse.getMsg()).thenReturn("rate limited");
        when(zhipuAiClient.chat().createChatCompletion(any(ChatCompletionCreateParams.class)))
                .thenReturn(failedResponse);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> converter.convertToStemXml("text"));
        assertEquals("GLM stem XML conversion failed: rate limited", ex.getMessage());
    }

    /**
     * Uses RETURNS_DEEP_STUBS to mock the entire response chain without needing
     * SDK-internal class imports (CompletionData, ChatChoice, etc.).
     */
    private void mockSuccessResponse(String content) {
        ChatCompletionResponse response = mock(ChatCompletionResponse.class, RETURNS_DEEP_STUBS);
        when(response.isSuccess()).thenReturn(true);
        // Deep stub: response.getData().getChoices().get(0).getMessage().getContent()
        when(response.getData().getChoices().get(0).getMessage().getContent()).thenReturn(content);

        when(zhipuAiClient.chat().createChatCompletion(any(ChatCompletionCreateParams.class)))
                .thenReturn(response);
    }
}
