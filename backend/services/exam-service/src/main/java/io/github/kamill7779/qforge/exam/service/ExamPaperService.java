package io.github.kamill7779.qforge.exam.service;

import io.github.kamill7779.qforge.internal.api.QuestionCoreClient;
import io.github.kamill7779.qforge.internal.api.QuestionSummaryDTO;
import io.github.kamill7779.qforge.exam.config.QForgeExamProperties;
import io.github.kamill7779.qforge.exam.dto.ExportWordRequest;
import io.github.kamill7779.qforge.exam.dto.exam.CreateExamPaperRequest;
import io.github.kamill7779.qforge.exam.dto.exam.ExamPaperDetailResponse;
import io.github.kamill7779.qforge.exam.dto.exam.ExamPaperExportRequest;
import io.github.kamill7779.qforge.exam.dto.exam.ExamPaperOverviewResponse;
import io.github.kamill7779.qforge.exam.dto.exam.ExamQuestionResponse;
import io.github.kamill7779.qforge.exam.dto.exam.ExamSectionResponse;
import io.github.kamill7779.qforge.exam.dto.exam.SaveExamContentRequest;
import io.github.kamill7779.qforge.exam.dto.exam.UpdateExamPaperRequest;
import io.github.kamill7779.qforge.exam.dto.export.ExportSectionPayload;
import io.github.kamill7779.qforge.exam.entity.ExamPaper;
import io.github.kamill7779.qforge.exam.entity.ExamQuestion;
import io.github.kamill7779.qforge.exam.entity.ExamSection;
import io.github.kamill7779.qforge.exam.entity.QuestionBasket;
import io.github.kamill7779.qforge.exam.exception.BusinessValidationException;
import io.github.kamill7779.qforge.exam.repository.ExamPaperRepository;
import io.github.kamill7779.qforge.exam.repository.ExamQuestionRepository;
import io.github.kamill7779.qforge.exam.repository.ExamSectionRepository;
import io.github.kamill7779.qforge.exam.repository.QuestionBasketRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 试卷组卷服务 — CRUD + 内容保存 + 导出。
 * 跨服务调用 question-core-service 通过 QuestionCoreClient Feign。
 */
@Service
public class ExamPaperService {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperService.class);

    private final ExamPaperRepository examPaperRepository;
    private final ExamSectionRepository examSectionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionBasketRepository basketRepository;
    private final QuestionCoreClient questionCoreClient;
    private final ExportService exportService;
    private final QForgeExamProperties examProperties;

    public ExamPaperService(
            ExamPaperRepository examPaperRepository,
            ExamSectionRepository examSectionRepository,
            ExamQuestionRepository examQuestionRepository,
            QuestionBasketRepository basketRepository,
            QuestionCoreClient questionCoreClient,
            ExportService exportService,
            QForgeExamProperties examProperties
    ) {
        this.examPaperRepository = examPaperRepository;
        this.examSectionRepository = examSectionRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.basketRepository = basketRepository;
        this.questionCoreClient = questionCoreClient;
        this.exportService = exportService;
        this.examProperties = examProperties;
    }

    // ───────── 列表查询 ─────────

    public List<ExamPaperOverviewResponse> listPapers(String requestUser) {
        List<ExamPaper> papers = examPaperRepository.findByOwnerUser(requestUser);
        if (papers.isEmpty()) {
            return List.of();
        }

        List<Long> paperIds = papers.stream().map(ExamPaper::getId).toList();
        List<ExamSection> allSections = examSectionRepository.findByPaperIds(paperIds);
        Map<Long, List<ExamSection>> sectionsByPaper = allSections.stream()
                .collect(Collectors.groupingBy(ExamSection::getPaperId));
        Map<Long, Integer> questionCountByPaper = new HashMap<>();

        List<Long> sectionIds = allSections.stream().map(ExamSection::getId).toList();
        if (!sectionIds.isEmpty()) {
            Map<Long, Long> sectionToPaper = allSections.stream()
                    .collect(Collectors.toMap(ExamSection::getId, ExamSection::getPaperId));
            for (ExamQuestion question : examQuestionRepository.findBySectionIds(sectionIds)) {
                Long paperId = sectionToPaper.get(question.getSectionId());
                if (paperId != null) {
                    questionCountByPaper.merge(paperId, 1, Integer::sum);
                }
            }
        }

        return papers.stream().map(p -> new ExamPaperOverviewResponse(
                p.getPaperUuid(),
                p.getTitle(),
                p.getSubtitle(),
                p.getStatus(),
                p.getDurationMinutes(),
                p.getTotalScore(),
                sectionsByPaper.getOrDefault(p.getId(), List.of()).size(),
                questionCountByPaper.getOrDefault(p.getId(), 0),
                p.getCreatedAt(),
                p.getUpdatedAt()
        )).toList();
    }

    // ───────── 创建 ─────────

    public ExamPaperDetailResponse createPaper(CreateExamPaperRequest request, String requestUser) {
        ExamPaper paper = new ExamPaper();
        paper.setPaperUuid(UUID.randomUUID().toString());
        paper.setOwnerUser(requestUser);
        paper.setTitle(request.getTitle() != null && !request.getTitle().isBlank()
                ? request.getTitle() : "未命名试卷");
        paper.setSubtitle(request.getSubtitle());
        paper.setDescription(request.getDescription());
        paper.setDurationMinutes(request.getDurationMinutes() != null
                ? request.getDurationMinutes()
                : examProperties.getDefaultDurationMinutes());
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setStatus("DRAFT");
        paper.setDeleted(false);

        examPaperRepository.save(paper);
        log.info("User [{}] created exam paper: {}", requestUser, paper.getPaperUuid());

        return toDetailResponse(paper, List.of());
    }

    // ───────── 详情查询 ─────────

    public ExamPaperDetailResponse getPaperDetail(String paperUuid, String requestUser) {
        ExamPaper paper = requirePaper(paperUuid, requestUser);
        List<ExamSection> sections = examSectionRepository.findByPaperId(paper.getId());
        List<Long> sectionIds = sections.stream().map(ExamSection::getId).toList();
        List<ExamQuestion> allQuestions = examQuestionRepository.findBySectionIds(sectionIds);

        // Feign 调用 question-core-service 批量获取摘要
        List<String> questionUuids = allQuestions.stream().map(ExamQuestion::getQuestionUuid).distinct().toList();
        Map<String, QuestionSummaryDTO> summaryMap = new HashMap<>();
        if (!questionUuids.isEmpty()) {
            try {
                List<QuestionSummaryDTO> summaries = questionCoreClient.batchGetSummaries(
                        String.join(",", questionUuids), requestUser);
                summaryMap = summaries.stream()
                        .collect(Collectors.toMap(QuestionSummaryDTO::questionUuid, s -> s));
            } catch (Exception e) {
                log.warn("Failed to fetch question summaries: {}", e.getMessage());
            }
        }

        Map<Long, List<ExamQuestion>> questionsBySection = allQuestions.stream()
                .collect(Collectors.groupingBy(ExamQuestion::getSectionId));

        Map<String, QuestionSummaryDTO> finalSummaryMap = summaryMap;
        List<ExamSectionResponse> sectionResponses = sections.stream().map(s -> {
            List<ExamQuestion> sqs = questionsBySection.getOrDefault(s.getId(), List.of());
            List<ExamQuestionResponse> qrs = sqs.stream().map(eq -> {
                QuestionSummaryDTO summary = finalSummaryMap.get(eq.getQuestionUuid());
                return new ExamQuestionResponse(
                        eq.getQuestionUuid(),
                        summary != null ? summary.stemText() : null,
                        eq.getScore(),
                        eq.getSortOrder() != null ? eq.getSortOrder() : 0,
                        eq.getNote()
                );
            }).toList();
            return new ExamSectionResponse(
                    s.getSectionUuid(),
                    s.getTitle(),
                    s.getDescription(),
                    s.getQuestionTypeCode(),
                    s.getDefaultScore(),
                    s.getSortOrder() != null ? s.getSortOrder() : 0,
                    qrs
            );
        }).toList();

        return toDetailResponse(paper, sectionResponses);
    }

    // ───────── 更新元信息 ─────────

    public ExamPaperDetailResponse updatePaper(String paperUuid, UpdateExamPaperRequest request, String requestUser) {
        ExamPaper paper = requirePaper(paperUuid, requestUser);

        if (request.getTitle() != null) paper.setTitle(request.getTitle());
        if (request.getSubtitle() != null) paper.setSubtitle(request.getSubtitle());
        if (request.getDescription() != null) paper.setDescription(request.getDescription());
        if (request.getDurationMinutes() != null) paper.setDurationMinutes(request.getDurationMinutes());
        if (request.getStatus() != null) paper.setStatus(request.getStatus());

        examPaperRepository.save(paper);
        return getPaperDetail(paperUuid, requestUser);
    }

    // ───────── 删除 ─────────

    public void deletePaper(String paperUuid, String requestUser) {
        ExamPaper paper = requirePaper(paperUuid, requestUser);
        examPaperRepository.deleteById(paper.getId());
        log.info("User [{}] deleted exam paper: {}", requestUser, paperUuid);
    }

    // ───────── 整卷内容保存（原子替换） ─────────

    @Transactional
    public ExamPaperDetailResponse saveContent(String paperUuid, SaveExamContentRequest request, String requestUser) {
        ExamPaper paper = requirePaper(paperUuid, requestUser);

        // 1. 删除旧内容
        List<ExamSection> oldSections = examSectionRepository.findByPaperId(paper.getId());
        List<Long> oldSectionIds = oldSections.stream().map(ExamSection::getId).toList();
        if (!oldSectionIds.isEmpty()) {
            examQuestionRepository.deleteBySectionIds(oldSectionIds);
        }
        examSectionRepository.deleteByPaperId(paper.getId());

        // 2. 写入新内容
        BigDecimal totalScore = BigDecimal.ZERO;
        List<SaveExamContentRequest.SectionPayload> sectionPayloads =
                request.getSections() != null ? request.getSections() : List.of();

        // 收集所有题目 UUID，通过 Feign 批量校验存在性并获取摘要
        List<String> allQuestionUuids = new ArrayList<>();
        for (SaveExamContentRequest.SectionPayload sp : sectionPayloads) {
            if (sp.getQuestions() != null) {
                allQuestionUuids.addAll(sp.getQuestions().stream()
                        .map(SaveExamContentRequest.QuestionPayload::getQuestionUuid).toList());
            }
        }

        Map<String, QuestionSummaryDTO> summaryMap = new HashMap<>();
        if (!allQuestionUuids.isEmpty()) {
            try {
                List<QuestionSummaryDTO> summaries = questionCoreClient.batchGetSummaries(
                        String.join(",", allQuestionUuids), requestUser);
                summaryMap = summaries.stream()
                        .collect(Collectors.toMap(QuestionSummaryDTO::questionUuid, s -> s));
            } catch (Exception e) {
                log.warn("Failed to validate question UUIDs via question-core: {}", e.getMessage());
            }
        }

        for (int si = 0; si < sectionPayloads.size(); si++) {
            SaveExamContentRequest.SectionPayload sp = sectionPayloads.get(si);

            ExamSection section = new ExamSection();
            section.setSectionUuid(sp.getSectionUuid() != null && !sp.getSectionUuid().isBlank()
                    ? sp.getSectionUuid() : UUID.randomUUID().toString());
            section.setPaperId(paper.getId());
            section.setTitle(sp.getTitle());
            section.setDescription(sp.getDescription());
            section.setQuestionTypeCode(sp.getQuestionTypeCode());
            section.setDefaultScore(sp.getDefaultScore() != null
                    ? sp.getDefaultScore()
                    : examProperties.getDefaultQuestionScore());
            section.setSortOrder(si);
            examSectionRepository.save(section);

            List<SaveExamContentRequest.QuestionPayload> questions =
                    sp.getQuestions() != null ? sp.getQuestions() : List.of();

            for (int qi = 0; qi < questions.size(); qi++) {
                SaveExamContentRequest.QuestionPayload qp = questions.get(qi);

                // 校验题目存在性（通过 Feign 结果）
                QuestionSummaryDTO summary = summaryMap.get(qp.getQuestionUuid());
                if (summary == null) {
                    log.warn("Question UUID {} not found for user {}, skipping", qp.getQuestionUuid(), requestUser);
                    continue;
                }

                ExamQuestion eq = new ExamQuestion();
                eq.setSectionId(section.getId());
                // questionId 不再通过本地查询获取，存 0 或使用 UUID-only 关联
                eq.setQuestionId(0L);
                eq.setQuestionUuid(qp.getQuestionUuid());
                eq.setSortOrder(qi);
                eq.setScore(qp.getScore() != null ? qp.getScore() : section.getDefaultScore());
                eq.setNote(qp.getNote());
                examQuestionRepository.save(eq);

                totalScore = totalScore.add(eq.getScore());
            }
        }

        paper.setTotalScore(totalScore);
        examPaperRepository.save(paper);

        log.info("User [{}] saved content for paper {}: {} sections, total {}",
                requestUser, paperUuid, sectionPayloads.size(), totalScore);

        return getPaperDetail(paperUuid, requestUser);
    }

    // ───────── 导出 Word ─────────

    public byte[] exportPaperWord(String paperUuid, ExamPaperExportRequest request, String requestUser) {
        ExamPaper paper = requirePaper(paperUuid, requestUser);
        List<ExamSection> sections = examSectionRepository.findByPaperId(paper.getId());
        List<Long> sectionIds = sections.stream().map(ExamSection::getId).toList();
        List<ExamQuestion> allQuestions = examQuestionRepository.findBySectionIds(sectionIds);

        Map<Long, List<ExamQuestion>> questionsBySection = allQuestions.stream()
                .collect(Collectors.groupingBy(ExamQuestion::getSectionId));

        List<ExportSectionPayload> exportSections = new ArrayList<>();
        List<String> allUuids = new ArrayList<>();

        for (ExamSection section : sections) {
            List<ExamQuestion> sqs = questionsBySection.getOrDefault(section.getId(), List.of());
            List<String> uuids = sqs.stream().map(ExamQuestion::getQuestionUuid).toList();
            exportSections.add(new ExportSectionPayload(section.getTitle(), uuids));
            allUuids.addAll(uuids);
        }

        if (allUuids.isEmpty()) {
            throw new BusinessValidationException("EXPORT_NO_QUESTIONS", "试卷中没有题目",
                    Map.of(), HttpStatus.BAD_REQUEST);
        }

        ExportWordRequest exportRequest = new ExportWordRequest(
                allUuids,
                paper.getTitle(),
                request.includeAnswers(),
                request.safeAnswerPosition(),
                exportSections
        );

        return exportService.exportQuestionsWord(exportRequest, requestUser);
    }

    // ───────── 从试题篮创建试卷 ─────────

    @Transactional
    public ExamPaperDetailResponse createFromBasket(String requestUser) {
        List<QuestionBasket> basketItems = basketRepository.findByOwnerUser(requestUser);
        if (basketItems.isEmpty()) {
            throw new BusinessValidationException("BASKET_EMPTY", "试题篮为空，无法创建试卷",
                    Map.of(), HttpStatus.BAD_REQUEST);
        }

        // Feign 批量获取摘要
        List<String> uuids = basketItems.stream().map(QuestionBasket::getQuestionUuid).toList();
        Map<String, QuestionSummaryDTO> summaryMap;
        try {
            List<QuestionSummaryDTO> summaries = questionCoreClient.batchGetSummaries(
                    String.join(",", uuids), requestUser);
            summaryMap = summaries.stream()
                    .collect(Collectors.toMap(QuestionSummaryDTO::questionUuid, s -> s));
        } catch (Exception e) {
            log.error("Failed to fetch question summaries from question-core-service", e);
            throw new BusinessValidationException("CORE_SERVICE_UNAVAILABLE",
                    "题目核心服务不可用", Map.of(), HttpStatus.BAD_GATEWAY);
        }

        ExamPaper paper = new ExamPaper();
        paper.setPaperUuid(UUID.randomUUID().toString());
        paper.setOwnerUser(requestUser);
        paper.setTitle("试题篮组卷 — " + basketItems.size() + " 题");
        paper.setDurationMinutes(examProperties.getDefaultDurationMinutes());
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setStatus("DRAFT");
        paper.setDeleted(false);
        examPaperRepository.save(paper);

        ExamSection section = new ExamSection();
        section.setSectionUuid(UUID.randomUUID().toString());
        section.setPaperId(paper.getId());
        section.setTitle("全部题目");
        section.setDefaultScore(examProperties.getDefaultQuestionScore());
        section.setSortOrder(0);
        examSectionRepository.save(section);

        BigDecimal totalScore = BigDecimal.ZERO;
        int sortOrder = 0;
        for (QuestionBasket basketItem : basketItems) {
            QuestionSummaryDTO summary = summaryMap.get(basketItem.getQuestionUuid());
            if (summary == null) continue;

            ExamQuestion eq = new ExamQuestion();
            eq.setSectionId(section.getId());
            eq.setQuestionId(0L);
            eq.setQuestionUuid(basketItem.getQuestionUuid());
            eq.setSortOrder(sortOrder++);
            eq.setScore(section.getDefaultScore());
            examQuestionRepository.save(eq);
            totalScore = totalScore.add(eq.getScore());
        }

        paper.setTotalScore(totalScore);
        examPaperRepository.save(paper);

        log.info("User [{}] created exam paper from basket: {} with {} questions",
                requestUser, paper.getPaperUuid(), sortOrder);

        return getPaperDetail(paper.getPaperUuid(), requestUser);
    }

    // ───────── 内部工具 ─────────

    private ExamPaper requirePaper(String paperUuid, String requestUser) {
        return examPaperRepository.findByPaperUuidAndOwnerUser(paperUuid, requestUser)
                .orElseThrow(() -> new BusinessValidationException(
                        "PAPER_NOT_FOUND", "试卷不存在",
                        Map.of("paperUuid", paperUuid), HttpStatus.NOT_FOUND));
    }

    private ExamPaperDetailResponse toDetailResponse(ExamPaper paper, List<ExamSectionResponse> sections) {
        return new ExamPaperDetailResponse(
                paper.getPaperUuid(),
                paper.getTitle(),
                paper.getSubtitle(),
                paper.getDescription(),
                paper.getDurationMinutes(),
                paper.getTotalScore(),
                paper.getStatus(),
                sections,
                paper.getCreatedAt(),
                paper.getUpdatedAt()
        );
    }
}
