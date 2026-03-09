package io.github.kamill7779.qforge.gaokaocorpus.controller;

import io.github.kamill7779.qforge.gaokaocorpus.dto.PhotoQueryRequest;
import io.github.kamill7779.qforge.gaokaocorpus.dto.PhotoQueryResponse;
import io.github.kamill7779.qforge.gaokaocorpus.client.GaokaoAnalysisClient;
import io.github.kamill7779.qforge.gaokaocorpus.client.PhotoQueryInternalRequest;
import io.github.kamill7779.qforge.gaokaocorpus.client.PhotoQueryInternalResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gaokao/photo-query")
public class PhotoQueryController {

    private static final Logger log = LoggerFactory.getLogger(PhotoQueryController.class);

    private final GaokaoAnalysisClient analysisClient;

    public PhotoQueryController(GaokaoAnalysisClient analysisClient) {
        this.analysisClient = analysisClient;
    }

    @PostMapping
    public ResponseEntity<PhotoQueryResponse> photoQuery(
            @Valid @RequestBody PhotoQueryRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        PhotoQueryInternalRequest internalReq = new PhotoQueryInternalRequest();
        internalReq.setImageBase64(request.getImageBase64());
        internalReq.setStorageRef(request.getStorageRef());
        internalReq.setTopK(request.getTopK() != null ? request.getTopK() : 10);

        PhotoQueryInternalResponse internalResp = analysisClient.photoQuery(internalReq);

        PhotoQueryResponse response = new PhotoQueryResponse();
        List<PhotoQueryResponse.MatchResult> results = new ArrayList<>();
        if (internalResp.getRecommendGroups() != null) {
            for (PhotoQueryInternalResponse.RecommendGroup group : internalResp.getRecommendGroups()) {
                if (group.getQuestions() != null) {
                    for (PhotoQueryInternalResponse.RecommendedQuestion rq : group.getQuestions()) {
                        PhotoQueryResponse.MatchResult mr = new PhotoQueryResponse.MatchResult();
                        mr.setQuestionUuid(rq.getQuestionUuid());
                        mr.setStemText(rq.getStemText());
                        mr.setSimilarity(rq.getScore());
                        results.add(mr);
                    }
                }
            }
        }
        response.setResults(results);
        log.info("Photo query by user={}, results={}", requestUser, results.size());
        return ResponseEntity.ok(response);
    }
}
