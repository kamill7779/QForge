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
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftAnswer;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftAnswerAsset;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftOption;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftPaper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftProfilePreview;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftQuestionAsset;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftSection;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestOcrPage;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSession;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSourceFile;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftAnswerAssetMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftAnswerMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftOptionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftProfilePreviewMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftQuestionAssetMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftSectionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestOcrPageMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSessionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSourceFileMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final GkDraftQuestionAssetMapper draftQuestionAssetMapper;
    private final GkDraftAnswerAssetMapper draftAnswerAssetMapper;
    private final GkDraftProfilePreviewMapper draftProfilePreviewMapper;
    private final GkIngestSessionMapper ingestSessionMapper;
    private final GkIngestSourceFileMapper ingestSourceFileMapper;
    private final GkIngestOcrPageMapper ingestOcrPageMapper;
    private final GaokaoAnalysisClient analysisClient;

    public DraftServiceImpl(
            GkDraftPaperMapper draftPaperMapper,
            GkDraftSectionMapper draftSectionMapper,
            GkDraftQuestionMapper draftQuestionMapper,
            GkDraftOptionMapper draftOptionMapper,
            GkDraftAnswerMapper draftAnswerMapper,
            GkDraftQuestionAssetMapper draftQuestionAssetMapper,
            GkDraftAnswerAssetMapper draftAnswerAssetMapper,
            GkDraftProfilePreviewMapper draftProfilePreviewMapper,
            GkIngestSessionMapper ingestSessionMapper,
            GkIngestSourceFileMapper ingestSourceFileMapper,
            GkIngestOcrPageMapper ingestOcrPageMapper,
            GaokaoAnalysisClient analysisClient
    ) {
        this.draftPaperMapper = draftPaperMapper;
        this.draftSectionMapper = draftSectionMapper;
        this.draftQuestionMapper = draftQuestionMapper;
        this.draftOptionMapper = draftOptionMapper;
        this.draftAnswerMapper = draftAnswerMapper;
        this.draftQuestionAssetMapper = draftQuestionAssetMapper;
        this.draftAnswerAssetMapper = draftAnswerAssetMapper;
        this.draftProfilePreviewMapper = draftProfilePreviewMapper;
        this.ingestSessionMapper = ingestSessionMapper;
        this.ingestSourceFileMapper = ingestSourceFileMapper;
        this.ingestOcrPageMapper = ingestOcrPageMapper;
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
            paper = bootstrapDraftPaperIfMissing(session);
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
        if (request.getDraftSectionUuid() != null) {
            GkDraftSection section = draftSectionMapper.selectOne(
                    new LambdaQueryWrapper<GkDraftSection>()
                            .eq(GkDraftSection::getDraftSectionUuid, request.getDraftSectionUuid())
                            .last("LIMIT 1"));
            question.setDraftSectionId(section != null ? section.getId() : null);
        }
        if (request.getParentDraftQuestionUuid() != null) {
            GkDraftQuestion parent = draftQuestionMapper.selectOne(
                    new LambdaQueryWrapper<GkDraftQuestion>()
                            .eq(GkDraftQuestion::getDraftQuestionUuid, request.getParentDraftQuestionUuid())
                            .last("LIMIT 1"));
            question.setParentQuestionId(parent != null ? parent.getId() : null);
            question.setRootQuestionId(parent != null && parent.getRootQuestionId() != null ? parent.getRootQuestionId() :
                    parent != null ? parent.getId() : null);
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
        if (request.getOptions() != null) {
            replaceOptions(question.getId(), request.getOptions());
        }
        if (request.getAnswers() != null) {
            replaceAnswers(question.getId(), request.getAnswers());
        }
        if (request.getStemAssets() != null) {
            replaceStemAssets(question.getId(), request.getStemAssets());
        }
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
        req.setQuestions(questions.stream().map(question -> {
            AnalyzePaperRequest.QuestionPayload payload = new AnalyzePaperRequest.QuestionPayload();
            payload.setDraftQuestionId(question.getId());
            payload.setStemText(question.getStemText());
            payload.setStemXml(question.getStemXml());
            payload.setQuestionTypeCode(question.getQuestionTypeCode());
            return payload;
        }).collect(Collectors.toList()));
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
                .filter(q -> q.getParentQuestionId() == null)
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
        dto.setOptions(draftOptionMapper.selectList(
                        new LambdaQueryWrapper<GkDraftOption>()
                                .eq(GkDraftOption::getDraftQuestionId, q.getId())
                                .orderByAsc(GkDraftOption::getSortOrder))
                .stream()
                .map(this::toDraftOptionDTO)
                .collect(Collectors.toList()));
        dto.setAnswers(draftAnswerMapper.selectList(
                        new LambdaQueryWrapper<GkDraftAnswer>()
                                .eq(GkDraftAnswer::getDraftQuestionId, q.getId())
                                .orderByAsc(GkDraftAnswer::getSortOrder))
                .stream()
                .map(this::toDraftAnswerDTO)
                .collect(Collectors.toList()));
        dto.setStemAssets(draftQuestionAssetMapper.selectList(
                        new LambdaQueryWrapper<GkDraftQuestionAsset>()
                                .eq(GkDraftQuestionAsset::getDraftQuestionId, q.getId())
                                .orderByAsc(GkDraftQuestionAsset::getSortOrder))
                .stream()
                .map(this::toDraftAssetDTO)
                .collect(Collectors.toList()));
        GkDraftProfilePreview preview = draftProfilePreviewMapper.selectOne(
                new LambdaQueryWrapper<GkDraftProfilePreview>()
                        .eq(GkDraftProfilePreview::getDraftQuestionId, q.getId())
                        .orderByDesc(GkDraftProfilePreview::getProfileVersion)
                        .last("LIMIT 1"));
        dto.setAnalysisPreview(toAnalysisPreviewDTO(preview));
        List<GkDraftQuestion> childQuestions = draftQuestionMapper.selectList(
                new LambdaQueryWrapper<GkDraftQuestion>()
                        .eq(GkDraftQuestion::getParentQuestionId, q.getId())
                        .orderByAsc(GkDraftQuestion::getQuestionNo, GkDraftQuestion::getId));
        dto.setChildQuestions(childQuestions.stream().map(this::toQuestionDTO).collect(Collectors.toList()));
        return dto;
    }

    private GkDraftPaper bootstrapDraftPaperIfMissing(GkIngestSession session) {
        List<GkIngestOcrPage> ocrPages = ingestOcrPageMapper.selectList(
                new LambdaQueryWrapper<GkIngestOcrPage>()
                        .eq(GkIngestOcrPage::getSessionId, session.getId())
                        .orderByAsc(GkIngestOcrPage::getPageNo, GkIngestOcrPage::getId));
        if (ocrPages.isEmpty()) {
            throw new IllegalArgumentException("No draft paper for session: " + session.getSessionUuid());
        }
        List<GkIngestSourceFile> sourceFiles = ingestSourceFileMapper.selectList(
                new LambdaQueryWrapper<GkIngestSourceFile>()
                        .eq(GkIngestSourceFile::getSessionId, session.getId())
                        .orderByAsc(GkIngestSourceFile::getCreatedAt)
                        .orderByAsc(GkIngestSourceFile::getId));

        LocalDateTime now = LocalDateTime.now();
        GkDraftPaper paper = new GkDraftPaper();
        paper.setDraftPaperUuid(UUID.randomUUID().toString());
        paper.setSessionId(session.getId());
        paper.setPaperName(resolvePaperName(session, sourceFiles));
        paper.setPaperTypeCode("GAOKAO_TEMPLATE");
        paper.setExamYear(session.getExamYearGuess());
        paper.setProvinceCode(session.getProvinceCodeGuess());
        paper.setDurationMinutes(120);
        paper.setStatus("EDITING");
        paper.setCreatedAt(now);
        paper.setUpdatedAt(now);
        draftPaperMapper.insert(paper);

        GkDraftSection section = new GkDraftSection();
        section.setDraftSectionUuid(UUID.randomUUID().toString());
        section.setDraftPaperId(paper.getId());
        section.setSectionCode("OCR_TEMPLATE");
        section.setSectionTitle("OCR template imports");
        section.setSortOrder(1);
        section.setCreatedAt(now);
        section.setUpdatedAt(now);
        draftSectionMapper.insert(section);

        for (int index = 0; index < ocrPages.size(); index++) {
            GkIngestOcrPage ocrPage = ocrPages.get(index);
            GkDraftQuestion question = new GkDraftQuestion();
            question.setDraftQuestionUuid(UUID.randomUUID().toString());
            question.setDraftPaperId(paper.getId());
            question.setDraftSectionId(section.getId());
            question.setQuestionNo(String.valueOf(index + 1));
            question.setQuestionTypeCode("UNCLASSIFIED");
            question.setAnswerMode("SUBJECTIVE");
            question.setStemText(normalizeOcrText(ocrPage.getFullText()));
            question.setStemXml(buildStemXml(ocrPage.getFullText()));
            question.setNormalizedStemText(normalizeOcrText(ocrPage.getFullText()));
            question.setHasAnswer(false);
            question.setEditVersion(1);
            question.setCreatedAt(now);
            question.setUpdatedAt(now);
            draftQuestionMapper.insert(question);

            if (ocrPage.getPageImageRef() != null && !ocrPage.getPageImageRef().isBlank()) {
                GkDraftQuestionAsset asset = new GkDraftQuestionAsset();
                asset.setDraftQuestionId(question.getId());
                asset.setAssetType("OCR_PAGE_IMAGE");
                asset.setStorageRef(ocrPage.getPageImageRef());
                asset.setSortOrder(1);
                asset.setCreatedAt(now);
                draftQuestionAssetMapper.insert(asset);
            }
        }

        session.setStatus("EDITING");
        session.setPaperNameGuess(paper.getPaperName());
        session.setUpdatedAt(now);
        ingestSessionMapper.updateById(session);
        log.info("Bootstrapped draft paper for session={}, sourceFiles={}, draftPaperUuid={}",
                session.getSessionUuid(), sourceFiles.size(), paper.getDraftPaperUuid());
        return paper;
    }

    private String resolvePaperName(GkIngestSession session, List<GkIngestSourceFile> sourceFiles) {
        if (session.getPaperNameGuess() != null && !session.getPaperNameGuess().isBlank()) {
            return session.getPaperNameGuess();
        }
        String firstFileName = sourceFiles.get(0).getFileName();
        int dotIndex = firstFileName.lastIndexOf('.');
        return dotIndex > 0 ? firstFileName.substring(0, dotIndex) : firstFileName;
    }

    private String normalizeOcrText(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace('\r', '\n').replaceAll("\\n{2,}", "\n").trim();
    }

    private DraftQuestionDTO.DraftOptionDTO toDraftOptionDTO(GkDraftOption option) {
        DraftQuestionDTO.DraftOptionDTO dto = new DraftQuestionDTO.DraftOptionDTO();
        dto.setDraftOptionUuid(option.getDraftOptionUuid());
        dto.setOptionLabel(option.getOptionLabel());
        dto.setOptionText(option.getOptionText());
        dto.setOptionXml(option.getOptionXml());
        dto.setSortOrder(option.getSortOrder());
        return dto;
    }

    private DraftQuestionDTO.DraftAnswerDTO toDraftAnswerDTO(GkDraftAnswer answer) {
        DraftQuestionDTO.DraftAnswerDTO dto = new DraftQuestionDTO.DraftAnswerDTO();
        dto.setDraftAnswerUuid(answer.getDraftAnswerUuid());
        dto.setAnswerType(answer.getAnswerType());
        dto.setAnswerText(answer.getAnswerText());
        dto.setAnswerXml(answer.getAnswerXml());
        dto.setOfficial(answer.getIsOfficial());
        dto.setSortOrder(answer.getSortOrder());
        dto.setAssets(draftAnswerAssetMapper.selectList(
                        new LambdaQueryWrapper<GkDraftAnswerAsset>()
                                .eq(GkDraftAnswerAsset::getDraftAnswerId, answer.getId())
                                .orderByAsc(GkDraftAnswerAsset::getSortOrder))
                .stream()
                .map(this::toDraftAssetDTO)
                .collect(Collectors.toList()));
        return dto;
    }

    private DraftQuestionDTO.DraftAssetDTO toDraftAssetDTO(GkDraftQuestionAsset asset) {
        DraftQuestionDTO.DraftAssetDTO dto = new DraftQuestionDTO.DraftAssetDTO();
        dto.setAssetType(asset.getAssetType());
        dto.setStorageRef(asset.getStorageRef());
        dto.setSortOrder(asset.getSortOrder());
        return dto;
    }

    private DraftQuestionDTO.DraftAssetDTO toDraftAssetDTO(GkDraftAnswerAsset asset) {
        DraftQuestionDTO.DraftAssetDTO dto = new DraftQuestionDTO.DraftAssetDTO();
        dto.setAssetType(asset.getAssetType());
        dto.setStorageRef(asset.getStorageRef());
        dto.setSortOrder(asset.getSortOrder());
        return dto;
    }

    private void replaceOptions(Long draftQuestionId, List<UpdateDraftQuestionRequest.OptionPayload> options) {
        draftOptionMapper.delete(new LambdaQueryWrapper<GkDraftOption>()
                .eq(GkDraftOption::getDraftQuestionId, draftQuestionId));
        LocalDateTime now = LocalDateTime.now();
        for (UpdateDraftQuestionRequest.OptionPayload option : options) {
            GkDraftOption entity = new GkDraftOption();
            entity.setDraftOptionUuid(option.getDraftOptionUuid() != null ? option.getDraftOptionUuid() : UUID.randomUUID().toString());
            entity.setDraftQuestionId(draftQuestionId);
            entity.setOptionLabel(option.getOptionLabel());
            entity.setOptionText(option.getOptionText());
            entity.setOptionXml(option.getOptionXml());
            entity.setSortOrder(option.getSortOrder());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            draftOptionMapper.insert(entity);
        }
    }

    private void replaceAnswers(Long draftQuestionId, List<UpdateDraftQuestionRequest.AnswerPayload> answers) {
        List<GkDraftAnswer> existingAnswers = draftAnswerMapper.selectList(
                new LambdaQueryWrapper<GkDraftAnswer>().eq(GkDraftAnswer::getDraftQuestionId, draftQuestionId));
        for (GkDraftAnswer answer : existingAnswers) {
            draftAnswerAssetMapper.delete(new LambdaQueryWrapper<GkDraftAnswerAsset>()
                    .eq(GkDraftAnswerAsset::getDraftAnswerId, answer.getId()));
        }
        draftAnswerMapper.delete(new LambdaQueryWrapper<GkDraftAnswer>()
                .eq(GkDraftAnswer::getDraftQuestionId, draftQuestionId));

        LocalDateTime now = LocalDateTime.now();
        for (UpdateDraftQuestionRequest.AnswerPayload answer : answers) {
            GkDraftAnswer entity = new GkDraftAnswer();
            entity.setDraftAnswerUuid(answer.getDraftAnswerUuid() != null ? answer.getDraftAnswerUuid() : UUID.randomUUID().toString());
            entity.setDraftQuestionId(draftQuestionId);
            entity.setAnswerType(answer.getAnswerType());
            entity.setAnswerText(answer.getAnswerText());
            entity.setAnswerXml(answer.getAnswerXml());
            entity.setIsOfficial(answer.getOfficial());
            entity.setSortOrder(answer.getSortOrder());
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            draftAnswerMapper.insert(entity);

            if (answer.getAssets() != null) {
                for (UpdateDraftQuestionRequest.AssetPayload asset : answer.getAssets()) {
                    GkDraftAnswerAsset answerAsset = new GkDraftAnswerAsset();
                    answerAsset.setDraftAnswerId(entity.getId());
                    answerAsset.setAssetType(asset.getAssetType());
                    answerAsset.setStorageRef(asset.getStorageRef());
                    answerAsset.setSortOrder(asset.getSortOrder());
                    answerAsset.setCreatedAt(now);
                    draftAnswerAssetMapper.insert(answerAsset);
                }
            }
        }
    }

    private void replaceStemAssets(Long draftQuestionId, List<UpdateDraftQuestionRequest.AssetPayload> assets) {
        draftQuestionAssetMapper.delete(new LambdaQueryWrapper<GkDraftQuestionAsset>()
                .eq(GkDraftQuestionAsset::getDraftQuestionId, draftQuestionId));
        LocalDateTime now = LocalDateTime.now();
        for (UpdateDraftQuestionRequest.AssetPayload asset : assets) {
            GkDraftQuestionAsset entity = new GkDraftQuestionAsset();
            entity.setDraftQuestionId(draftQuestionId);
            entity.setAssetType(asset.getAssetType());
            entity.setStorageRef(asset.getStorageRef());
            entity.setSortOrder(asset.getSortOrder());
            entity.setCreatedAt(now);
            draftQuestionAssetMapper.insert(entity);
        }
    }

    private String buildStemXml(String rawText) {
        String normalized = normalizeOcrText(rawText);
        if (normalized.isBlank()) {
            return "<stem><p/></stem>";
        }
        return "<stem><p><![CDATA[" + escapeForCdata(normalized) + "]]></p></stem>";
    }

    private String escapeForCdata(String value) {
        return value.replace("]]>", "]]]]><![CDATA[>");
    }

    private DraftQuestionDTO.AnalysisPreviewDTO toAnalysisPreviewDTO(GkDraftProfilePreview preview) {
        if (preview == null) {
            return null;
        }
        DraftQuestionDTO.AnalysisPreviewDTO dto = new DraftQuestionDTO.AnalysisPreviewDTO();
        dto.setKnowledgeTagsJson(preview.getKnowledgeTagsJson());
        dto.setMethodTagsJson(preview.getMethodTagsJson());
        dto.setFormulaTagsJson(preview.getFormulaTagsJson());
        dto.setMistakeTagsJson(preview.getMistakeTagsJson());
        dto.setAbilityTagsJson(preview.getAbilityTagsJson());
        dto.setDifficultyScore(preview.getDifficultyScore());
        dto.setDifficultyLevel(preview.getDifficultyLevel());
        dto.setReasoningStepsJson(preview.getReasoningStepsJson());
        dto.setAnalysisSummaryText(preview.getAnalysisSummaryText());
        dto.setRecommendSeedText(preview.getRecommendSeedText());
        dto.setProfileVersion(preview.getProfileVersion());
        dto.setConfirmed(preview.getConfirmed());
        return dto;
    }
}
