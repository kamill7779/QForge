package io.github.kamill7779.qforge.gaokaoanalysis.client;

import io.github.kamill7779.qforge.common.contract.GaokaoIndexCallbackRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalysisResultDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "gaokao-corpus-service")
public interface GaokaoCorpusClient {

    @PutMapping("/internal/gaokao-corpus/draft-questions/{draftQuestionId}/profile")
    void updateDraftProfile(
            @PathVariable("draftQuestionId") Long draftQuestionId,
            @RequestBody AnalysisResultDTO result
    );

    @PutMapping("/internal/gaokao-corpus/papers/{paperId}/indexing")
    void updatePaperIndex(
            @PathVariable("paperId") Long paperId,
            @RequestBody GaokaoIndexCallbackRequest request
    );
}
