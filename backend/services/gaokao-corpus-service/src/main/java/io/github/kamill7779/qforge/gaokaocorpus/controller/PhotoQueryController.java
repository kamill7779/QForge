package io.github.kamill7779.qforge.gaokaocorpus.controller;

import io.github.kamill7779.qforge.gaokaocorpus.dto.PhotoQueryRequest;
import io.github.kamill7779.qforge.gaokaocorpus.dto.PhotoQueryResponse;
import io.github.kamill7779.qforge.gaokaocorpus.client.GaokaoAnalysisClient;
import io.github.kamill7779.qforge.gaokaocorpus.client.PhotoQueryInternalRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gaokao/photo-query")
public class PhotoQueryController {

    private final GaokaoAnalysisClient analysisClient;

    public PhotoQueryController(GaokaoAnalysisClient analysisClient) {
        this.analysisClient = analysisClient;
    }

    @PostMapping
    public ResponseEntity<PhotoQueryResponse> photoQuery(
            @Valid @RequestBody PhotoQueryRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        // TODO: implement — delegate to analysis-service, build PhotoQueryResponse from result
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
