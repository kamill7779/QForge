package io.github.kamill7779.qforge.gaokaocorpus.service;

import io.github.kamill7779.qforge.gaokaocorpus.dto.GkPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftAnswerMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftOptionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftProfilePreviewMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftSectionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSessionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkPaperSectionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionAnswerMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionOptionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionProfileMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishServiceImpl implements PublishService {

    private static final Logger log = LoggerFactory.getLogger(PublishServiceImpl.class);

    private final GkDraftPaperMapper draftPaperMapper;
    private final GkDraftSectionMapper draftSectionMapper;
    private final GkDraftQuestionMapper draftQuestionMapper;
    private final GkDraftOptionMapper draftOptionMapper;
    private final GkDraftAnswerMapper draftAnswerMapper;
    private final GkDraftProfilePreviewMapper draftProfilePreviewMapper;
    private final GkIngestSessionMapper ingestSessionMapper;
    private final GkPaperMapper paperMapper;
    private final GkPaperSectionMapper paperSectionMapper;
    private final GkQuestionMapper questionMapper;
    private final GkQuestionOptionMapper questionOptionMapper;
    private final GkQuestionAnswerMapper questionAnswerMapper;
    private final GkQuestionProfileMapper questionProfileMapper;

    public PublishServiceImpl(
            GkDraftPaperMapper draftPaperMapper,
            GkDraftSectionMapper draftSectionMapper,
            GkDraftQuestionMapper draftQuestionMapper,
            GkDraftOptionMapper draftOptionMapper,
            GkDraftAnswerMapper draftAnswerMapper,
            GkDraftProfilePreviewMapper draftProfilePreviewMapper,
            GkIngestSessionMapper ingestSessionMapper,
            GkPaperMapper paperMapper,
            GkPaperSectionMapper paperSectionMapper,
            GkQuestionMapper questionMapper,
            GkQuestionOptionMapper questionOptionMapper,
            GkQuestionAnswerMapper questionAnswerMapper,
            GkQuestionProfileMapper questionProfileMapper
    ) {
        this.draftPaperMapper = draftPaperMapper;
        this.draftSectionMapper = draftSectionMapper;
        this.draftQuestionMapper = draftQuestionMapper;
        this.draftOptionMapper = draftOptionMapper;
        this.draftAnswerMapper = draftAnswerMapper;
        this.draftProfilePreviewMapper = draftProfilePreviewMapper;
        this.ingestSessionMapper = ingestSessionMapper;
        this.paperMapper = paperMapper;
        this.paperSectionMapper = paperSectionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionProfileMapper = questionProfileMapper;
    }

    @Override
    @Transactional
    public GkPaperDTO publishPaper(String draftPaperUuid) {
        // TODO: implement — copy from draft tables to published tables
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
