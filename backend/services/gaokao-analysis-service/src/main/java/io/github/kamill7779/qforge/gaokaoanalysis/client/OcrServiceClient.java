package io.github.kamill7779.qforge.gaokaoanalysis.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ocr-service")
public interface OcrServiceClient {

    @PostMapping("/internal/ocr/recognize")
    OcrRecognizeResponse recognize(@RequestBody OcrRecognizeRequest request);
}
