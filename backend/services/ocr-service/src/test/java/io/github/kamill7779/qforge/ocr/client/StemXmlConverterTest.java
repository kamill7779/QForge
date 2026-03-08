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
import io.github.kamill7779.qforge.ocr.config.QForgeOcrProperties;
import io.github.kamill7779.qforge.ocr.config.StemXmlProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StemXmlConverterTest {

    private ZhipuAiClient zhipuAiClient;
    private StemXmlProperties properties;
    private QForgeOcrProperties ocrProperties;
    private StemXmlConverter converter;

    @BeforeEach
    void setUp() {
        zhipuAiClient = mock(ZhipuAiClient.class, RETURNS_DEEP_STUBS);

        properties = new StemXmlProperties();
        properties.setModel("glm-4-0520");
        properties.setTemperature(0.1f);
        properties.setMaxTokens(65536);

        ocrProperties = new QForgeOcrProperties();

        converter = new StemXmlConverter(zhipuAiClient, properties, ocrProperties);
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

    @Test
    void shouldThrowWhenContentIsEmptyAfterRetries() {
        // GLM returns empty content twice (MAX_RETRIES = 2)
        mockSuccessResponse("");

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> converter.convertToStemXml("text"));
        assert ex.getMessage().contains("empty content after 2 attempts");
    }

    @Test
    void shouldThrowWhenContentIsNullAfterRetries() {
        mockSuccessResponse(null);

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> converter.convertToStemXml("text"));
        assert ex.getMessage().contains("empty content after 2 attempts");
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
