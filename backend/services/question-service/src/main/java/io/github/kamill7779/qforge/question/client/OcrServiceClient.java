package io.github.kamill7779.qforge.question.client;

import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "ocr-service", url = "${clients.ocr.base-url}")
public interface OcrServiceClient {

    @PostMapping("/internal/ocr/tasks")
    OcrTaskAcceptedResponse createTask(@RequestBody OcrServiceCreateTaskRequest request);
}

