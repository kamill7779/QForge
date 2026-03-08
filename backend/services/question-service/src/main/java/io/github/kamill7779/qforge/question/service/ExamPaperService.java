package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.question.dto.ExportWordRequest;
import io.github.kamill7779.qforge.question.dto.exam.CreateExamPaperRequest;
import io.github.kamill7779.qforge.question.dto.exam.ExamPaperDetailResponse;
import io.github.kamill7779.qforge.question.dto.exam.ExamPaperExportRequest;
import io.github.kamill7779.qforge.question.dto.exam.ExamPaperOverviewResponse;
import io.github.kamill7779.qforge.question.dto.exam.ExamQuestionResponse;
import io.github.kamill7779.qforge.question.dto.exam.ExamSectionResponse;
import io.github.kamill7779.qforge.question.dto.exam.SaveExamContentRequest;
import io.github.kamill7779.qforge.question.dto.exam.UpdateExamPaperRequest;
import io.github.kamill7779.qforge.question.dto.export.ExportSectionPayload;
import io.github.kamill7779.qforge.question.entity.ExamPaper;
import io.github.kamill7779.qforge.question.entity.ExamQuestion;
import io.github.kamill7779.qforge.question.entity.ExamSection;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionBasket;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.repository.ExamPaperRepository;
import io.github.kamill7779.qforge.question.repository.ExamQuestionRepository;
import io.github.kamill7779.qforge.question.repository.ExamSectionRepository;
import io.github.kamill7779.qforge.question.repository.QuestionBasketRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
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
 */
@Service
public class ExamPaperService {

    private static final Logger log = LoggerFactory.getLogger(ExamPaperService.class);

    private final ExamPaperRepository examPaperRepository;
    private final ExamSectionRepository examSectionRepository;
    private final ExamQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final QuestionBasketRepository basketRepository;
    private final ExportService exportService;

    public ExamPaperService(
            ExamPaperRepository examPaperRepository,
            ExamSectionRepository examSectionRepository,
            ExamQuestionRepository examQuestionRepository,
            QuestionRepository questionRepository,
            QuestionBasketRepository basketRepository,
            ExportService exportService
    ) {
        this.examPaperRepository = examPaperRepository;
        this.examSectionRepository = examSectionRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.questionRepository = questionRepository;
        this.basketRepository = basketRepository;
        this.exportService = exportService;
    }

    // ───────── 列表查询 ─────────

    public List<ExamPaperOverviewResponse> listPapers(String requestUser) {
        List<ExamPaper> papers = examPaperRepository.findByOwnerUser(requestUser);

        // 批量统计每张试卷的大题 / 题目数
        List<Long> paperIds = papers.stream().map(ExamPaper::getId).toList();
        Map<Long, List<ExamSection>> sectionsByPaper = new HashMap<>();
        Map<Long, Integer> questionCountByPaper = new HashMap<>();

        if (!paperIds.isEmpty()) {
            for (Long pid : paperIds) {
                List<ExamSection> sections = examSectionRepository.findByPaperId(pid);
                sectionsByPaper.put(pid, sections);
                List<Long> sids = sections.stream().map(ExamSection::getId).toList();
                List<ExamQuestion> questions = examQuestionRepository.findBySectionIds(sids);
                questionCountByPaper.put(pid, questions.size());
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
        paper.setDurationMinutes(request.getDurationMinutes() != null ? request.getDurationMinutes() : 120);
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

        // 批量加载题目 stem_text
        List<String> questionUuids = allQuestions.stream().map(ExamQuestion::getQuestionUuid).distinct().toList();
        Map<String, Question> questionMap = new HashMap<>();
        if (!questionUuids.isEmpty()) {
            List<Question> questions = questionRepository.findByQuestionUuidsAndOwnerUser(questionUuids, requestUser);
            questionMap = questions.stream().collect(Collectors.toMap(Question::getQuestionUuid, q -> q));
        }

        // 按 sectionId 分组
        Map<Long, List<ExamQuestion>> questionsBySection = allQuestions.stream()
                .collect(Collectors.groupingBy(ExamQuestion::getSectionId));

        Map<String, Question> finalQuestionMap = questionMap;
        List<ExamSectionResponse> sectionResponses = sections.stream().map(s -> {
            List<ExamQuestion> sqs = questionsBySection.getOrDefault(s.getId(), List.of());
            List<ExamQuestionResponse> qrs = sqs.stream().map(eq -> {
                Question q = finalQuestionMap.get(eq.getQuestionUuid());
                return new ExamQuestionResponse(
                        eq.getQuestionUuid(),
                        q != null ? q.getStemText() : null,
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
        examPaperRepository.deleteById(paper.getId()); // @TableLogic → 逻辑删除
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

        for (int si = 0; si < sectionPayloads.size(); si++) {
            SaveExamContentRequest.SectionPayload sp = sectionPayloads.get(si);

            ExamSection section = new ExamSection();
            section.setSectionUuid(sp.getSectionUuid() != null && !sp.getSectionUuid().isBlank()
                    ? sp.getSectionUuid() : UUID.randomUUID().toString());
            section.setPaperId(paper.getId());
            section.setTitle(sp.getTitle());
            section.setDescription(sp.getDescription());
            section.setQuestionTypeCode(sp.getQuestionTypeCode());
            section.setDefaultScore(sp.getDefaultScore() != null ? sp.getDefaultScore() : new BigDecimal("5.0"));
            section.setSortOrder(si);
            examSectionRepository.save(section);

            List<SaveExamContentRequest.QuestionPayload> questions =
                    sp.getQuestions() != null ? sp.getQuestions() : List.of();

            // 批量解析 questionUuid → questionId
            List<String> uuids = questions.stream().map(SaveExamContentRequest.QuestionPayload::getQuestionUuid).toList();
            Map<String, Long> uuidToId = new HashMap<>();
            if (!uuids.isEmpty()) {
                List<Question> matched = questionRepository.findByQuestionUuidsAndOwnerUser(uuids, requestUser);
                uuidToId = matched.stream().collect(Collectors.toMap(Question::getQuestionUuid, Question::getId));
            }

            for (int qi = 0; qi < questions.size(); qi++) {
                SaveExamContentRequest.QuestionPayload qp = questions.get(qi);
                Long questionId = uuidToId.get(qp.getQuestionUuid());
                if (questionId == null) {
                    log.warn("Question UUID {} not found for user {}, skipping", qp.getQuestionUuid(), requestUser);
                    continue;
                }

                ExamQuestion eq = new ExamQuestion();
                eq.setSectionId(section.getId());
                eq.setQuestionId(questionId);
                eq.setQuestionUuid(qp.getQuestionUuid());
                eq.setSortOrder(qi);
                eq.setScore(qp.getScore() != null ? qp.getScore() : section.getDefaultScore());
                eq.setNote(qp.getNote());
                examQuestionRepository.save(eq);

                totalScore = totalScore.add(eq.getScore());
            }
        }

        // 3. 更新总分
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

        // 构建 ExportWordRequest，复用现有 ExportService
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
        // 1. 读取试题篮
        List<QuestionBasket> basketItems = basketRepository.findByOwnerUser(requestUser);
        if (basketItems.isEmpty()) {
            throw new BusinessValidationException("BASKET_EMPTY", "试题篮为空，无法创建试卷",
                    Map.of(), HttpStatus.BAD_REQUEST);
        }

        // 2. 批量加载题目信息
        List<String> uuids = basketItems.stream().map(QuestionBasket::getQuestionUuid).toList();
        List<Question> questions = questionRepository.findByQuestionUuidsAndOwnerUser(uuids, requestUser);
        Map<String, Question> questionMap = questions.stream()
                .collect(Collectors.toMap(Question::getQuestionUuid, q -> q));

        // 3. 创建试卷
        ExamPaper paper = new ExamPaper();
        paper.setPaperUuid(UUID.randomUUID().toString());
        paper.setOwnerUser(requestUser);
        paper.setTitle("试题篮组卷 — " + basketItems.size() + " 题");
        paper.setDurationMinutes(120);
        paper.setTotalScore(BigDecimal.ZERO);
        paper.setStatus("DRAFT");
        paper.setDeleted(false);
        examPaperRepository.save(paper);

        // 4. 创建默认 section，将所有题目放入
        ExamSection section = new ExamSection();
        section.setSectionUuid(UUID.randomUUID().toString());
        section.setPaperId(paper.getId());
        section.setTitle("全部题目");
        section.setDefaultScore(new BigDecimal("5.0"));
        section.setSortOrder(0);
        examSectionRepository.save(section);

        BigDecimal totalScore = BigDecimal.ZERO;
        int sortOrder = 0;
        for (QuestionBasket basketItem : basketItems) {
            Question q = questionMap.get(basketItem.getQuestionUuid());
            if (q == null) continue;

            ExamQuestion eq = new ExamQuestion();
            eq.setSectionId(section.getId());
            eq.setQuestionId(q.getId());
            eq.setQuestionUuid(q.getQuestionUuid());
            eq.setSortOrder(sortOrder++);
            eq.setScore(section.getDefaultScore());
            examQuestionRepository.save(eq);
            totalScore = totalScore.add(eq.getScore());
        }

        // 5. 更新总分
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
