package io.github.kamill7779.qforge.internal.api;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * ocr-service 的高考整卷分题内部 API。
 */
@FeignClient(name = "ocr-service")
public interface GaokaoSplitClient {

    @PostMapping("/internal/ocr/gaokao-split")
    GaokaoSplitResponse splitExam(@RequestBody GaokaoSplitRequest request);
}