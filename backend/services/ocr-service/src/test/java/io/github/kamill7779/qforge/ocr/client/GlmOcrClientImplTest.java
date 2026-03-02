package io.github.kamill7779.qforge.ocr.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketException;
import java.util.Map;
import javax.net.ssl.SSLHandshakeException;
import io.github.kamill7779.qforge.ocr.config.OcrProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.mockito.Mockito;

class GlmOcrClientImplTest {

    private static final String EXPECTED_PROMPT = String.join("\n",
            "You are an OCR for Chinese Gaokao math problems.",
            "1. Extract ONLY the question text, requirements, options (A/B/C/D).",
            "2. IGNORE: all images, graphs, geometry figures, coordinate systems, watermarks, borders, page numbers.",
            "3. Convert all mathematical formulas to standard LaTeX:",
            "   - fractions: \\frac{}{}, square root: \\sqrt{}, subscripts: _ , superscripts: ^",
            "   - vector: \\vec{}, set: \\in, \\cup, \\cap, \\emptyset",
            "   - inline: $...$, display: $$...$$",
            "4. Remove non-question metadata/watermark lines, especially source/channel text such as "
                    + "'wechat official account', 'source', 'scan code', 'advertisement', page headers/footers.",
            "5. Output ONLY pure LaTeX, no extra words, no comments, no descriptions."
    );

    @Test
    void shouldCallOfficialLayoutParsingApiWithBearerToken() {
        OcrProviderProperties properties = new OcrProviderProperties();
        properties.setEndpoint("https://api.z.ai/api/paas/v4/layout_parsing");
        properties.setModel("glm-ocr");
        properties.setApiKey("test-api-key");
        properties.setImageMimeType("image/png");
        properties.setTimeoutSeconds(60);

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("https://api.z.ai/api/paas/v4/layout_parsing"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-api-key"))
                .andExpect(jsonPath("$.model").value("glm-ocr"))
                .andExpect(jsonPath("$.file").value("data:image/png;base64,img-base64"))
                .andExpect(jsonPath("$.prompt").value(EXPECTED_PROMPT))
                .andRespond(withSuccess("{\"md_results\":\"ok\"}", MediaType.APPLICATION_JSON));

        GlmOcrClient client = new GlmOcrClientImpl(restTemplate, properties);
        String result = client.recognizeText("img-base64");

        assertEquals("ok", result);
        server.verify();
    }

    @Test
    void shouldRetryWhenNetworkIsUnreachableThenSucceed() throws Exception {
        OcrProviderProperties properties = new OcrProviderProperties();
        properties.setEndpoint("https://api.z.ai/api/paas/v4/layout_parsing");
        properties.setModel("glm-ocr");
        properties.setApiKey("test-api-key");
        properties.setImageMimeType("image/png");
        properties.setTimeoutSeconds(60);
        properties.setRetryBackoffMillis(0);
        properties.setRetryMaxAttempts(3);

        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        ResponseEntity<Map> success = new ResponseEntity<>(Map.of("md_results", "ok"), HttpStatusCode.valueOf(200));
        when(restTemplate.exchange(
                eq("https://api.z.ai/api/paas/v4/layout_parsing"),
                eq(HttpMethod.POST),
                any(),
                eq(Map.class)))
                .thenThrow(new ResourceAccessException("I/O error on POST request", new SocketException("Network is unreachable")))
                .thenReturn(success);

        GlmOcrClient client = new GlmOcrClientImpl(restTemplate, properties);
        String result = client.recognizeText("img-base64");

        assertEquals("ok", result);
        verify(restTemplate, times(2)).exchange(
                eq("https://api.z.ai/api/paas/v4/layout_parsing"),
                eq(HttpMethod.POST),
                any(),
                eq(Map.class));
    }

    @Test
    void shouldRetryWhenTlsHandshakeIsTerminatedThenSucceed() throws Exception {
        OcrProviderProperties properties = new OcrProviderProperties();
        properties.setEndpoint("https://api.z.ai/api/paas/v4/layout_parsing");
        properties.setModel("glm-ocr");
        properties.setApiKey("test-api-key");
        properties.setImageMimeType("image/png");
        properties.setTimeoutSeconds(60);
        properties.setRetryBackoffMillis(0);
        properties.setRetryMaxAttempts(3);

        RestTemplate restTemplate = Mockito.mock(RestTemplate.class);
        ResponseEntity<Map> success = new ResponseEntity<>(Map.of("md_results", "ok"), HttpStatusCode.valueOf(200));
        when(restTemplate.exchange(
                eq("https://api.z.ai/api/paas/v4/layout_parsing"),
                eq(HttpMethod.POST),
                any(),
                eq(Map.class)))
                .thenThrow(new ResourceAccessException("I/O error on POST request", new SSLHandshakeException("Remote host terminated the handshake")))
                .thenReturn(success);

        GlmOcrClient client = new GlmOcrClientImpl(restTemplate, properties);
        String result = client.recognizeText("img-base64");

        assertEquals("ok", result);
        verify(restTemplate, times(2)).exchange(
                eq("https://api.z.ai/api/paas/v4/layout_parsing"),
                eq(HttpMethod.POST),
                any(),
                eq(Map.class));
    }
}
