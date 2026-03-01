package io.github.kamill7779.qforge.ocr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

class OfficialGlmOcrLayoutParsingSmokeTest {

    private static final String DEFAULT_ENDPOINT = "https://api.z.ai/api/paas/v4/layout_parsing";
    private static final String DEFAULT_MODEL = "glm-ocr";
    private static final String DEFAULT_FILE = "https://cdn.bigmodel.cn/static/logo/introduction.png";
    private static final String DEFAULT_PROMPT = String.join("\n",
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
    void shouldComparePromptEffectOnOfficialLayoutParsingApi() throws Exception {
        String apiKey = System.getenv("ZHIPU_API_KEY");
        assertNotNull(apiKey, "Missing env var: ZHIPU_API_KEY");
        assertFalse(apiKey.isBlank(), "Empty env var: ZHIPU_API_KEY");

        String endpoint = envOrDefault("ZHIPU_LAYOUT_PARSING_ENDPOINT", DEFAULT_ENDPOINT);
        String model = envOrDefault("ZHIPU_LAYOUT_PARSING_MODEL", DEFAULT_MODEL);
        String file = resolveFileArg(envOrDefault("ZHIPU_LAYOUT_PARSING_FILE", DEFAULT_FILE));

        RestTemplate restTemplate = new RestTemplate();

        Map<?, ?> withoutPrompt = callLayoutParsing(restTemplate, endpoint, apiKey, model, file, null);
        Map<?, ?> withPrompt = callLayoutParsing(restTemplate, endpoint, apiKey, model, file, DEFAULT_PROMPT);

        assertEquals(model, String.valueOf(withoutPrompt.get("model")), "Unexpected model in response without prompt");
        assertEquals(model, String.valueOf(withPrompt.get("model")), "Unexpected model in response with prompt");
        assertTrue(hasResultField(withoutPrompt), "Response without prompt should include md_results or layout_details");
        assertTrue(hasResultField(withPrompt), "Response with prompt should include md_results or layout_details");

        String mdWithout = Objects.toString(withoutPrompt.get("md_results"), "");
        String mdWith = Objects.toString(withPrompt.get("md_results"), "");

        System.out.println("===== OFFICIAL GLM-OCR WITHOUT PROMPT =====");
        System.out.println(mdWithout);
        System.out.println("===== OFFICIAL GLM-OCR WITH COMPLEX PROMPT =====");
        System.out.println(mdWith);
        System.out.println("===== PROMPT EFFECT SUMMARY =====");
        System.out.println("without_prompt_len=" + mdWithout.length() + ", with_prompt_len=" + mdWith.length());
    }

    private static String envOrDefault(String key, String defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return value;
    }

    private static boolean hasResultField(Map<?, ?> body) {
        return body.containsKey("md_results") || body.containsKey("layout_details");
    }

    private static Map<?, ?> callLayoutParsing(
            RestTemplate restTemplate,
            String endpoint,
            String apiKey,
            String model,
            String file,
            String prompt
    ) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", model);
        payload.put("file", file);
        if (prompt != null && !prompt.isBlank()) {
            payload.put("prompt", prompt);
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                endpoint,
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Map.class
        );
        assertEquals(200, response.getStatusCode().value(), "Expected HTTP 200 from official layout parsing API");
        assertNotNull(response.getBody(), "Response body should not be null");
        return response.getBody();
    }

    private static String resolveFileArg(String fileArg) throws Exception {
        if (fileArg.startsWith("http://") || fileArg.startsWith("https://") || fileArg.startsWith("data:")) {
            return fileArg;
        }

        Path path = Path.of(fileArg);
        if (Files.exists(path)) {
            byte[] bytes = Files.readAllBytes(path);
            String base64 = Base64.getEncoder().encodeToString(bytes);
            String lower = path.getFileName().toString().toLowerCase();
            String mimeType = lower.endsWith(".jpg") || lower.endsWith(".jpeg") ? "image/jpeg" : "image/png";
            return "data:" + mimeType + ";base64," + base64;
        }
        return fileArg;
    }
}
