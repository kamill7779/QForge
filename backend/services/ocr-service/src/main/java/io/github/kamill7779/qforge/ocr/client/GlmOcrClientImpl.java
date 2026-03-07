package io.github.kamill7779.qforge.ocr.client;

import io.github.kamill7779.qforge.ocr.config.OcrProviderProperties;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.net.ssl.SSLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Component
public class GlmOcrClientImpl implements GlmOcrClient {

    private static final Logger log = LoggerFactory.getLogger(GlmOcrClientImpl.class);

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
        return recognizeText(imageBase64, OCR_PROMPT);
    }

    @Override
    public String recognizeText(String imageBase64, String customPrompt) {
        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            log.error("GLM OCR apiKey is missing or blank");
            throw new RestClientException("GLM OCR apiKey is missing");
        }

        String prompt = (customPrompt != null && !customPrompt.isBlank()) ? customPrompt : OCR_PROMPT;

        log.info("Starting OCR recognition (endpoint={}, model={}, imageLen={})",
                properties.getEndpoint(), properties.getModel(),
                imageBase64 != null ? imageBase64.length() : 0);

        int maxAttempts = Math.max(1, properties.getRetryMaxAttempts());
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String result = doRecognizeText(imageBase64, prompt);
                log.info("OCR recognition succeeded (resultLen={})", result != null ? result.length() : 0);
                return result;
            } catch (RestClientException ex) {
                log.warn("OCR attempt {}/{} failed: {}", attempt, maxAttempts, ex.getMessage());
                if (!isRetriableNetworkException(ex) || attempt >= maxAttempts) {
                    log.error("OCR recognition failed after {} attempts", attempt, ex);
                    throw ex;
                }
                sleepBeforeRetry();
            }
        }
        throw new RestClientException("GLM OCR request failed after retry attempts");
    }

    private String doRecognizeText(String imageBase64, String prompt) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(properties.getApiKey());
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel());
        payload.put("file", toLayoutParsingFile(imageBase64));
        payload.put("prompt", prompt);

        ResponseEntity<Map> responseEntity;
        try {
            responseEntity = restTemplate.exchange(
                    properties.getEndpoint(),
                    HttpMethod.POST,
                    new HttpEntity<>(payload, headers),
                    Map.class
            );
        } catch (HttpStatusCodeException httpEx) {
            log.error("GLM OCR HTTP error: status={}, body={}",
                    httpEx.getStatusCode(), httpEx.getResponseBodyAsString(), httpEx);
            throw httpEx;
        }
        Map<?, ?> response = responseEntity.getBody();
        log.debug("GLM OCR response status={}, body={}",
                responseEntity.getStatusCode(), response);

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

    private boolean isRetriableNetworkException(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (cursor instanceof NoRouteToHostException
                    || cursor instanceof ConnectException
                    || cursor instanceof SocketTimeoutException
                    || cursor instanceof UnknownHostException
                    || cursor instanceof SSLException
                    || cursor instanceof SocketException) {
                return true;
            }
            cursor = cursor.getCause();
        }

        String message = throwable.getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains("network is unreachable");
    }

    private void sleepBeforeRetry() {
        long backoffMillis = Math.max(0L, properties.getRetryBackoffMillis());
        if (backoffMillis == 0L) {
            return;
        }
        try {
            Thread.sleep(backoffMillis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new RestClientException("GLM OCR retry interrupted", ex);
        }
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
        // Auto-detect MIME type from base64 magic bytes
        String mimeType = detectMimeFromBase64(imageBase64);
        return "data:" + mimeType + ";base64," + imageBase64;
    }

    /**
     * Detect image MIME type from the first few characters of a base64-encoded string.
     * Falls back to the configured default or image/png.
     */
    private String detectMimeFromBase64(String base64) {
        if (base64.length() >= 4) {
            // PNG: starts with iVBOR (base64 of 0x89 P N G)
            if (base64.startsWith("iVBOR")) return "image/png";
            // JPEG: starts with /9j/ (base64 of 0xFF 0xD8 0xFF)
            if (base64.startsWith("/9j/")) return "image/jpeg";
            // PDF: starts with JVBER (base64 of %PDF)
            if (base64.startsWith("JVBER")) return "application/pdf";
            // GIF: starts with R0lG (base64 of GIF8)
            if (base64.startsWith("R0lG")) return "image/gif";
            // WebP: starts with UklG (base64 of RIFF)
            if (base64.startsWith("UklG")) return "image/png"; // Zhipu doesn't support webp, treat as png fallback
        }
        String fallback = properties.getImageMimeType();
        if (fallback != null && !fallback.isBlank()) return fallback;
        return "image/png";
    }
}
