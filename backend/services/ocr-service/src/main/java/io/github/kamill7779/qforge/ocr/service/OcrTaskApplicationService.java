package io.github.kamill7779.qforge.ocr.service;

import io.github.kamill7779.qforge.ocr.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.ocr.dto.OcrTaskCreateRequest;

public interface OcrTaskApplicationService {

    OcrTaskAcceptedResponse createTask(OcrTaskCreateRequest request);
}

