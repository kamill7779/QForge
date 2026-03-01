package io.github.kamill7779.qforge.ocr.controller;

import io.github.kamill7779.qforge.ocr.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.ocr.dto.OcrTaskCreateRequest;
import io.github.kamill7779.qforge.ocr.service.OcrTaskApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ocr/tasks")
public class OcrTaskController {

    private final OcrTaskApplicationService ocrTaskApplicationService;

    public OcrTaskController(OcrTaskApplicationService ocrTaskApplicationService) {
        this.ocrTaskApplicationService = ocrTaskApplicationService;
    }

    @PostMapping
    public ResponseEntity<OcrTaskAcceptedResponse> createTask(@Valid @RequestBody OcrTaskCreateRequest request) {
        return ResponseEntity.accepted().body(ocrTaskApplicationService.createTask(request));
    }
}

