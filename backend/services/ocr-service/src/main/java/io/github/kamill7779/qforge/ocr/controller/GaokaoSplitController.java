package io.github.kamill7779.qforge.ocr.controller;

import io.github.kamill7779.qforge.internal.api.GaokaoSplitRequest;
import io.github.kamill7779.qforge.internal.api.GaokaoSplitResponse;
import io.github.kamill7779.qforge.ocr.service.GaokaoSplitService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/ocr")
public class GaokaoSplitController {

    private final GaokaoSplitService gaokaoSplitService;

    public GaokaoSplitController(GaokaoSplitService gaokaoSplitService) {
        this.gaokaoSplitService = gaokaoSplitService;
    }

    @PostMapping("/gaokao-split")
    public ResponseEntity<GaokaoSplitResponse> split(@Valid @RequestBody GaokaoSplitRequest request) {
        return ResponseEntity.ok(gaokaoSplitService.split(request));
    }
}