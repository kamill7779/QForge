package io.github.kamill7779.qforge.ocr.client;

import io.github.kamill7779.qforge.ocr.config.OcrProviderProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class GlmOcrClientImpl implements GlmOcrClient {

    private static final String OCR_PROMPT = String.join("\n",
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

    private final RestTemplate restTemplate;
    private final OcrProviderProperties properties;

    public GlmOcrClientImpl(RestTemplate restTemplate, OcrProviderProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    @Override
    public String recognizeText(String imageBase64) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new RestClientException("GLM OCR apiKey is missing");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("file", toLayoutParsingFile(imageBase64));
        payload.put("prompt", OCR_PROMPT);

        ResponseEntity<Map> responseEntity = restTemplate.exchange(
                properties.getEndpoint(),
                HttpMethod.POST,
                new HttpEntity<>(payload, headers),
                Map.class
        );
        Map<?, ?> response = responseEntity.getBody();

        if (response == null) {
            throw new RestClientException("GLM OCR response body is null");
        }

        Object mdResults = response.get("md_results");
        if (mdResults != null) {
            return String.valueOf(mdResults);
        }

        Object layoutDetails = response.get("layout_details");
        if (layoutDetails != null) {
            return String.valueOf(layoutDetails);
        }

        throw new RestClientException("GLM OCR response missing 'md_results' and 'layout_details' fields");
    }

    private String toLayoutParsingFile(String imageBase64) {
        if (imageBase64 == null || imageBase64.isBlank()) {
            throw new RestClientException("OCR image base64 is empty");
        }
        if (imageBase64.startsWith("http://")
                || imageBase64.startsWith("https://")
                || imageBase64.startsWith("data:")) {
            return imageBase64;
        }
        String mimeType = properties.getImageMimeType();
        if (mimeType == null || mimeType.isBlank()) {
            mimeType = "image/png";
        }
        return "data:" + mimeType + ";base64," + imageBase64;
    }
}
