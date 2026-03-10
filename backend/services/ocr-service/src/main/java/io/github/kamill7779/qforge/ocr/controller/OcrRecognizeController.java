package io.github.kamill7779.qforge.ocr.controller;

import io.github.kamill7779.qforge.ocr.client.GlmOcrClient;
import io.github.kamill7779.qforge.ocr.dto.OcrRecognizeRequest;
import io.github.kamill7779.qforge.ocr.dto.OcrRecognizeResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ocr")
public class OcrRecognizeController {

    private final GlmOcrClient glmOcrClient;

    public OcrRecognizeController(GlmOcrClient glmOcrClient) {
        this.glmOcrClient = glmOcrClient;
    }

    @PostMapping("/recognize")
    public ResponseEntity<OcrRecognizeResponse> recognize(@Valid @RequestBody OcrRecognizeRequest request) {
        String fullText = glmOcrClient.recognizeText(request.getImageBase64());
        OcrRecognizeResponse response = new OcrRecognizeResponse();
        response.setFullText(fullText);
        response.setLayoutJson("[]");
        response.setFormulaJson("[]");
        return ResponseEntity.ok(response);
    }
}
