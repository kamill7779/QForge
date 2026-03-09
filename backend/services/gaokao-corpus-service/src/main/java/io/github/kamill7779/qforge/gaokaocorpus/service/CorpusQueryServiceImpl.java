package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkQuestionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkPaperSectionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionProfileMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkRecommendEdgeMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CorpusQueryServiceImpl implements CorpusQueryService {

    private static final Logger log = LoggerFactory.getLogger(CorpusQueryServiceImpl.class);

    private final GkPaperMapper paperMapper;
    private final GkPaperSectionMapper paperSectionMapper;
    private final GkQuestionMapper questionMapper;
    private final GkQuestionProfileMapper questionProfileMapper;
    private final GkRecommendEdgeMapper recommendEdgeMapper;

    public CorpusQueryServiceImpl(
            GkPaperMapper paperMapper,
            GkPaperSectionMapper paperSectionMapper,
            GkQuestionMapper questionMapper,
            GkQuestionProfileMapper questionProfileMapper,
            GkRecommendEdgeMapper recommendEdgeMapper
    ) {
        this.paperMapper = paperMapper;
        this.paperSectionMapper = paperSectionMapper;
        this.questionMapper = questionMapper;
        this.questionProfileMapper = questionProfileMapper;
        this.recommendEdgeMapper = recommendEdgeMapper;
    }

    @Override
    public GkPaperDTO getPaper(String paperUuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public Page<GkPaperDTO> listPapers(Short examYear, String provinceCode, String paperTypeCode, int page, int size) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public GkQuestionDTO getQuestion(String questionUuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<GkQuestionDTO> searchSimilar(String questionUuid) {
        // TODO: implement — query gk_recommend_edge for similar questions
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
