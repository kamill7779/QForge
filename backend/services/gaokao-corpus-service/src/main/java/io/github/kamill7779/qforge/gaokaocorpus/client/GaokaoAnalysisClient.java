package io.github.kamill7779.qforge.gaokaocorpus.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "gaokao-analysis-service")
public interface GaokaoAnalysisClient {

    @PostMapping("/internal/gaokao-analysis/analyze-question")
    void analyzeQuestion(@RequestBody AnalyzeQuestionRequest request);

    @PostMapping("/internal/gaokao-analysis/analyze-paper")
    void analyzePaper(@RequestBody AnalyzePaperRequest request);

    @PostMapping("/internal/gaokao-analysis/photo-query")
    PhotoQueryInternalResponse photoQuery(@RequestBody PhotoQueryInternalRequest request);
}
