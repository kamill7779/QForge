package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.github.kamill7779.qforge.gaokaocorpus.client.AnalyzePaperRequest;
import io.github.kamill7779.qforge.gaokaocorpus.client.AnalyzeQuestionRequest;
import io.github.kamill7779.qforge.gaokaocorpus.client.GaokaoAnalysisClient;
import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftQuestionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.UpdateDraftPaperRequest;
import io.github.kamill7779.qforge.gaokaocorpus.dto.UpdateDraftQuestionRequest;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftPaper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftProfilePreview;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftSection;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSession;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftAnswerMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftOptionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftProfilePreviewMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftSectionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSessionMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftServiceImpl implements DraftService {

    private static final Logger log = LoggerFactory.getLogger(DraftServiceImpl.class);

    private final GkDraftPaperMapper draftPaperMapper;
    private final GkDraftSectionMapper draftSectionMapper;
    private final GkDraftQuestionMapper draftQuestionMapper;
    private final GkDraftOptionMapper draftOptionMapper;
    private final GkDraftAnswerMapper draftAnswerMapper;
    private final GkDraftProfilePreviewMapper draftProfilePreviewMapper;
    private final GkIngestSessionMapper ingestSessionMapper;
    private final GaokaoAnalysisClient analysisClient;

    public DraftServiceImpl(
            GkDraftPaperMapper draftPaperMapper,
            GkDraftSectionMapper draftSectionMapper,
            GkDraftQuestionMapper draftQuestionMapper,
            GkDraftOptionMapper draftOptionMapper,
            GkDraftAnswerMapper draftAnswerMapper,
            GkDraftProfilePreviewMapper draftProfilePreviewMapper,
            GkIngestSessionMapper ingestSessionMapper,
            GaokaoAnalysisClient analysisClient
    ) {
        this.draftPaperMapper = draftPaperMapper;
        this.draftSectionMapper = draftSectionMapper;
        this.draftQuestionMapper = draftQuestionMapper;
        this.draftOptionMapper = draftOptionMapper;
        this.draftAnswerMapper = draftAnswerMapper;
        this.draftProfilePreviewMapper = draftProfilePreviewMapper;
        this.ingestSessionMapper = ingestSessionMapper;
        this.analysisClient = analysisClient;
    }

    @Override
    public DraftPaperDTO getDraftPaper(String sessionUuid) {
        GkIngestSession session = ingestSessionMapper.selectOne(
                new LambdaQueryWrapper<GkIngestSession>()
                        .eq(GkIngestSession::getSessionUuid, sessionUuid));
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionUuid);
        }
        GkDraftPaper paper = draftPaperMapper.selectOne(
                new LambdaQueryWrapper<GkDraftPaper>()
                        .eq(GkDraftPaper::getSessionId, session.getId()));
        if (paper == null) {
            throw new IllegalArgumentException("No draft paper for session: " + sessionUuid);
        }
        return toPaperDTO(paper);
    }

    @Override
    @Transactional
    public DraftPaperDTO updateDraftPaper(String draftPaperUuid, UpdateDraftPaperRequest request) {
        GkDraftPaper paper = draftPaperMapper.selectOne(
                new LambdaQueryWrapper<GkDraftPaper>()
                        .eq(GkDraftPaper::getDraftPaperUuid, draftPaperUuid));
        if (paper == null) {
            throw new IllegalArgumentException("Draft paper not found: " + draftPaperUuid);
        }
        if (request.getPaperName() != null) paper.setPaperName(request.getPaperName());
        if (request.getPaperTypeCode() != null) paper.setPaperTypeCode(request.getPaperTypeCode());
        if (request.getExamYear() != null) paper.setExamYear(request.getExamYear());
        if (request.getProvinceCode() != null) paper.setProvinceCode(request.getProvinceCode());
        if (request.getTotalScore() != null) paper.setTotalScore(request.getTotalScore());
        if (request.getDurationMinutes() != null) paper.setDurationMinutes(request.getDurationMinutes());
        paper.setUpdatedAt(LocalDateTime.now());
        draftPaperMapper.updateById(paper);
        log.info("Updated draft paper: {}", draftPaperUuid);
        return toPaperDTO(paper);
    }

    @Override
    @Transactional
    public DraftQuestionDTO updateDraftQuestion(String draftQuestionUuid, UpdateDraftQuestionRequest request) {
        GkDraftQuestion question = draftQuestionMapper.selectOne(
                new LambdaQueryWrapper<GkDraftQuestion>()
                        .eq(GkDraftQuestion::getDraftQuestionUuid, draftQuestionUuid));
        if (question == null) {
            throw new IllegalArgumentException("Draft question not found: " + draftQuestionUuid);
        }
        if (request.getQuestionNo() != null) question.setQuestionNo(request.getQuestionNo());
        if (request.getQuestionTypeCode() != null) question.setQuestionTypeCode(request.getQuestionTypeCode());
        if (request.getAnswerMode() != null) question.setAnswerMode(request.getAnswerMode());
        if (request.getStemText() != null) question.setStemText(request.getStemText());
        if (request.getStemXml() != null) question.setStemXml(request.getStemXml());
        if (request.getScore() != null) question.setScore(request.getScore());
        if (request.getEditVersion() != null) question.setEditVersion(request.getEditVersion());
        question.setUpdatedAt(LocalDateTime.now());
        draftQuestionMapper.updateById(question);
        log.info("Updated draft question: {}", draftQuestionUuid);
        return toQuestionDTO(question);
    }

    @Override
    public void triggerAnalyze(String draftQuestionUuid) {
        GkDraftQuestion question = draftQuestionMapper.selectOne(
                new LambdaQueryWrapper<GkDraftQuestion>()
                        .eq(GkDraftQuestion::getDraftQuestionUuid, draftQuestionUuid));
        if (question == null) {
            throw new IllegalArgumentException("Draft question not found: " + draftQuestionUuid);
        }
        AnalyzeQuestionRequest req = new AnalyzeQuestionRequest();
        req.setDraftQuestionId(question.getId());
        req.setStemText(question.getStemText());
        req.setStemXml(question.getStemXml());
        req.setQuestionTypeCode(question.getQuestionTypeCode());
        try {
            analysisClient.analyzeQuestion(req);
            log.info("Triggered analysis for question: {}", draftQuestionUuid);
        } catch (Exception e) {
            log.warn("Analysis service call failed for question {}: {}", draftQuestionUuid, e.getMessage());
        }
    }

    @Override
    public void triggerBatchAnalyze(String draftPaperUuid) {
        GkDraftPaper paper = draftPaperMapper.selectOne(
                new LambdaQueryWrapper<GkDraftPaper>()
                        .eq(GkDraftPaper::getDraftPaperUuid, draftPaperUuid));
        if (paper == null) {
            throw new IllegalArgumentException("Draft paper not found: " + draftPaperUuid);
        }
        List<GkDraftQuestion> questions = draftQuestionMapper.selectList(
                new LambdaQueryWrapper<GkDraftQuestion>()
                        .eq(GkDraftQuestion::getDraftPaperId, paper.getId()));
        List<Long> questionIds = questions.stream().map(GkDraftQuestion::getId).collect(Collectors.toList());

        AnalyzePaperRequest req = new AnalyzePaperRequest();
        req.setDraftPaperUuid(draftPaperUuid);
        req.setDraftQuestionIds(questionIds);
        try {
            analysisClient.analyzePaper(req);
            paper.setStatus("ANALYZING");
            paper.setUpdatedAt(LocalDateTime.now());
            draftPaperMapper.updateById(paper);
            log.info("Triggered batch analysis for paper: {}, questions: {}", draftPaperUuid, questionIds.size());
        } catch (Exception e) {
            log.warn("Analysis service call failed for paper {}: {}", draftPaperUuid, e.getMessage());
        }
    }

    @Override
    @Transactional
    public void confirmProfile(String draftQuestionUuid) {
        GkDraftQuestion question = draftQuestionMapper.selectOne(
                new LambdaQueryWrapper<GkDraftQuestion>()
                        .eq(GkDraftQuestion::getDraftQuestionUuid, draftQuestionUuid));
        if (question == null) {
            throw new IllegalArgumentException("Draft question not found: " + draftQuestionUuid);
        }
        draftProfilePreviewMapper.update(null,
                new LambdaUpdateWrapper<GkDraftProfilePreview>()
                        .eq(GkDraftProfilePreview::getDraftQuestionId, question.getId())
                        .set(GkDraftProfilePreview::getConfirmed, true)
                        .set(GkDraftProfilePreview::getUpdatedAt, LocalDateTime.now()));
        log.info("Confirmed profile for question: {}", draftQuestionUuid);
    }

    private DraftPaperDTO toPaperDTO(GkDraftPaper paper) {
        DraftPaperDTO dto = new DraftPaperDTO();
        dto.setDraftPaperUuid(paper.getDraftPaperUuid());
        dto.setPaperName(paper.getPaperName());
        dto.setPaperTypeCode(paper.getPaperTypeCode());
        dto.setExamYear(paper.getExamYear());
        dto.setProvinceCode(paper.getProvinceCode());
        dto.setTotalScore(paper.getTotalScore());
        dto.setDurationMinutes(paper.getDurationMinutes());
        dto.setStatus(paper.getStatus());

        // Load sections with questions
        List<GkDraftSection> sections = draftSectionMapper.selectList(
                new LambdaQueryWrapper<GkDraftSection>()
                        .eq(GkDraftSection::getDraftPaperId, paper.getId())
                        .orderByAsc(GkDraftSection::getSortOrder));
        List<GkDraftQuestion> allQuestions = draftQuestionMapper.selectList(
                new LambdaQueryWrapper<GkDraftQuestion>()
                        .eq(GkDraftQuestion::getDraftPaperId, paper.getId()));
        Map<Long, List<GkDraftQuestion>> questionsBySectionId = allQuestions.stream()
                .filter(q -> q.getDraftSectionId() != null)
                .collect(Collectors.groupingBy(GkDraftQuestion::getDraftSectionId));

        List<DraftPaperDTO.DraftSectionDTO> sectionDTOs = new ArrayList<>();
        for (GkDraftSection sec : sections) {
            DraftPaperDTO.DraftSectionDTO secDTO = new DraftPaperDTO.DraftSectionDTO();
            secDTO.setDraftSectionUuid(sec.getDraftSectionUuid());
            secDTO.setSectionCode(sec.getSectionCode());
            secDTO.setSectionTitle(sec.getSectionTitle());
            secDTO.setSortOrder(sec.getSortOrder());
            List<GkDraftQuestion> secQuestions = questionsBySectionId.getOrDefault(sec.getId(), List.of());
            secDTO.setQuestions(secQuestions.stream().map(this::toQuestionDTO).collect(Collectors.toList()));
            sectionDTOs.add(secDTO);
        }
        dto.setSections(sectionDTOs);
        return dto;
    }

    private DraftQuestionDTO toQuestionDTO(GkDraftQuestion q) {
        DraftQuestionDTO dto = new DraftQuestionDTO();
        dto.setDraftQuestionUuid(q.getDraftQuestionUuid());
        dto.setQuestionNo(q.getQuestionNo());
        dto.setQuestionTypeCode(q.getQuestionTypeCode());
        dto.setAnswerMode(q.getAnswerMode());
        dto.setStemText(q.getStemText());
        dto.setStemXml(q.getStemXml());
        dto.setNormalizedStemText(q.getNormalizedStemText());
        dto.setScore(q.getScore());
        dto.setHasAnswer(q.getHasAnswer());
        dto.setEditVersion(q.getEditVersion());
        return dto;
    }
}
