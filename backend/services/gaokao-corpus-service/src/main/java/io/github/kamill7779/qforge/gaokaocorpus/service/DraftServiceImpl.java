package io.github.kamill7779.qforge.gaokaocorpus.service;

import io.github.kamill7779.qforge.gaokaocorpus.client.GaokaoAnalysisClient;
import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftQuestionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.UpdateDraftPaperRequest;
import io.github.kamill7779.qforge.gaokaocorpus.dto.UpdateDraftQuestionRequest;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftAnswerMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftOptionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftProfilePreviewMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftSectionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DraftServiceImpl implements DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftServiceImpl.class);

    private final GkDraftPaperMapper draftPaperMapper;
    private final GkDraftSectionMapper draftSectionMapper;
    private final GkDraftQuestionMapper draftQuestionMapper;
    private final GkDraftOptionMapper draftOptionMapper;
    private final GkDraftAnswerMapper draftAnswerMapper;
    private final GkDraftProfilePreviewMapper draftProfilePreviewMapper;
    private final GaokaoAnalysisClient analysisClient;

    public DraftServiceImpl(
            GkDraftPaperMapper draftPaperMapper,
            GkDraftSectionMapper draftSectionMapper,
            GkDraftQuestionMapper draftQuestionMapper,
            GkDraftOptionMapper draftOptionMapper,
            GkDraftAnswerMapper draftAnswerMapper,
            GkDraftProfilePreviewMapper draftProfilePreviewMapper,
            GaokaoAnalysisClient analysisClient
    ) {
        this.draftPaperMapper = draftPaperMapper;
        this.draftSectionMapper = draftSectionMapper;
        this.draftQuestionMapper = draftQuestionMapper;
        this.draftOptionMapper = draftOptionMapper;
        this.draftAnswerMapper = draftAnswerMapper;
        this.draftProfilePreviewMapper = draftProfilePreviewMapper;
        this.analysisClient = analysisClient;
    }

    @Override
    public DraftPaperDTO getDraftPaper(String sessionUuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public DraftPaperDTO updateDraftPaper(String draftPaperUuid, UpdateDraftPaperRequest request) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public DraftQuestionDTO updateDraftQuestion(String draftQuestionUuid, UpdateDraftQuestionRequest request) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void triggerAnalyze(String draftQuestionUuid) {
        // TODO: implement — delegate to gaokao-analysis-service via Feign
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void triggerBatchAnalyze(String draftPaperUuid) {
        // TODO: implement — delegate to gaokao-analysis-service via Feign
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void confirmProfile(String draftQuestionUuid) {
        // TODO: implement — mark draft_profile_preview as confirmed
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
