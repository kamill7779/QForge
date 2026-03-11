package io.github.kamill7779.qforge.gaokaocorpus.service;

import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftQuestionAssetDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftQuestionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.UpdateDraftPaperRequest;
import io.github.kamill7779.qforge.gaokaocorpus.dto.UpdateDraftQuestionRequest;
import java.util.List;

public interface DraftService {

    DraftPaperDTO getDraftPaper(String sessionUuid);

    DraftPaperDTO updateDraftPaper(String draftPaperUuid, UpdateDraftPaperRequest request);

    DraftQuestionDTO updateDraftQuestion(String draftQuestionUuid, UpdateDraftQuestionRequest request);

    void triggerAnalyze(String draftQuestionUuid);

    void triggerBatchAnalyze(String draftPaperUuid);

    void confirmProfile(String draftQuestionUuid);

    List<DraftQuestionAssetDTO> getQuestionAssets(String draftQuestionUuid);
}
