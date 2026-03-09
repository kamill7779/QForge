package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkQuestionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkPaper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkRecommendEdge;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkPaperSectionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionProfileMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkRecommendEdgeMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
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
        GkPaper paper = paperMapper.selectOne(
                new LambdaQueryWrapper<GkPaper>()
                        .eq(GkPaper::getPaperUuid, paperUuid));
        if (paper == null) {
            throw new IllegalArgumentException("Paper not found: " + paperUuid);
        }
        GkPaperDTO dto = toPaperDTO(paper);
        List<GkQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<GkQuestion>()
                        .eq(GkQuestion::getPaperId, paper.getId()));
        dto.setQuestions(questions.stream().map(this::toQuestionDTO).collect(Collectors.toList()));
        return dto;
    }

    @Override
    public Page<GkPaperDTO> listPapers(Short examYear, String provinceCode, String paperTypeCode, int page, int size) {
        LambdaQueryWrapper<GkPaper> wrapper = new LambdaQueryWrapper<GkPaper>()
                .eq(GkPaper::getStatus, "READY");
        if (examYear != null) wrapper.eq(GkPaper::getExamYear, examYear);
        if (provinceCode != null && !provinceCode.isBlank()) wrapper.eq(GkPaper::getProvinceCode, provinceCode);
        if (paperTypeCode != null && !paperTypeCode.isBlank()) wrapper.eq(GkPaper::getPaperTypeCode, paperTypeCode);
        wrapper.orderByDesc(GkPaper::getExamYear);

        Page<GkPaper> paperPage = paperMapper.selectPage(new Page<>(page, size), wrapper);

        Page<GkPaperDTO> dtoPage = new Page<>(paperPage.getCurrent(), paperPage.getSize(), paperPage.getTotal());
        dtoPage.setRecords(paperPage.getRecords().stream().map(this::toPaperDTO).collect(Collectors.toList()));
        return dtoPage;
    }

    @Override
    public GkQuestionDTO getQuestion(String questionUuid) {
        GkQuestion question = questionMapper.selectOne(
                new LambdaQueryWrapper<GkQuestion>()
                        .eq(GkQuestion::getQuestionUuid, questionUuid));
        if (question == null) {
            throw new IllegalArgumentException("Question not found: " + questionUuid);
        }
        GkQuestionDTO dto = toQuestionDTO(question);
        GkPaper paper = paperMapper.selectById(question.getPaperId());
        if (paper != null) {
            dto.setPaperUuid(paper.getPaperUuid());
        }
        return dto;
    }

    @Override
    public List<GkQuestionDTO> searchSimilar(String questionUuid) {
        GkQuestion source = questionMapper.selectOne(
                new LambdaQueryWrapper<GkQuestion>()
                        .eq(GkQuestion::getQuestionUuid, questionUuid));
        if (source == null) {
            return List.of();
        }
        List<GkRecommendEdge> edges = recommendEdgeMapper.selectList(
                new LambdaQueryWrapper<GkRecommendEdge>()
                        .eq(GkRecommendEdge::getSourceQuestionId, source.getId())
                        .orderByDesc(GkRecommendEdge::getScore)
                        .last("LIMIT 10"));
        if (edges.isEmpty()) {
            return List.of();
        }
        List<Long> targetIds = edges.stream().map(GkRecommendEdge::getTargetQuestionId).collect(Collectors.toList());
        List<GkQuestion> targets = questionMapper.selectBatchIds(targetIds);
        return targets.stream().map(this::toQuestionDTO).collect(Collectors.toList());
    }

    private GkPaperDTO toPaperDTO(GkPaper paper) {
        GkPaperDTO dto = new GkPaperDTO();
        dto.setPaperUuid(paper.getPaperUuid());
        dto.setPaperName(paper.getPaperName());
        dto.setPaperTypeCode(paper.getPaperTypeCode());
        dto.setExamYear(paper.getExamYear());
        dto.setProvinceCode(paper.getProvinceCode());
        dto.setSubjectCode(paper.getSubjectCode());
        dto.setStatus(paper.getStatus());
        return dto;
    }

    private GkQuestionDTO toQuestionDTO(GkQuestion q) {
        GkQuestionDTO dto = new GkQuestionDTO();
        dto.setQuestionUuid(q.getQuestionUuid());
        dto.setQuestionNo(q.getQuestionNo());
        dto.setQuestionTypeCode(q.getQuestionTypeCode());
        dto.setAnswerMode(q.getAnswerMode());
        dto.setStemText(q.getStemText());
        dto.setStemXml(q.getStemXml());
        dto.setNormalizedStemText(q.getNormalizedStemText());
        dto.setScore(q.getScore());
        dto.setDifficultyScore(q.getDifficultyScore());
        dto.setDifficultyLevel(q.getDifficultyLevel());
        dto.setReasoningStepCount(q.getReasoningStepCount());
        dto.setHasAnswer(q.getHasAnswer());
        return dto;
    }
}
