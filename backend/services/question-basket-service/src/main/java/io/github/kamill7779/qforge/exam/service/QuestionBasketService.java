package io.github.kamill7779.qforge.exam.service;

import io.github.kamill7779.qforge.exam.config.QForgeComposeProperties;
import io.github.kamill7779.qforge.internal.api.QuestionCoreClient;
import io.github.kamill7779.qforge.internal.api.BasketComposeCreateExamRequest;
import io.github.kamill7779.qforge.internal.api.BasketComposeQuestionCreateRequest;
import io.github.kamill7779.qforge.internal.api.BasketComposeSectionCreateRequest;
import io.github.kamill7779.qforge.internal.api.ExamPaperInternalClient;
import io.github.kamill7779.qforge.internal.api.InternalExamPaperDetailDTO;
import io.github.kamill7779.qforge.internal.api.InternalExamQuestionDTO;
import io.github.kamill7779.qforge.internal.api.InternalExamSectionDTO;
import io.github.kamill7779.qforge.internal.api.QuestionSummaryDTO;
import io.github.kamill7779.qforge.exam.dto.BasketItemResponse;
import io.github.kamill7779.qforge.exam.dto.compose.BasketComposeDetailResponse;
import io.github.kamill7779.qforge.exam.dto.compose.BasketComposeQuestionResponse;
import io.github.kamill7779.qforge.exam.dto.compose.BasketComposeSectionResponse;
import io.github.kamill7779.qforge.exam.dto.compose.SaveBasketComposeContentRequest;
import io.github.kamill7779.qforge.exam.dto.compose.UpdateBasketComposeMetaRequest;
import io.github.kamill7779.qforge.exam.dto.exam.ExamPaperDetailResponse;
import io.github.kamill7779.qforge.exam.dto.exam.ExamQuestionResponse;
import io.github.kamill7779.qforge.exam.dto.exam.ExamSectionResponse;
import io.github.kamill7779.qforge.exam.entity.BasketCompose;
import io.github.kamill7779.qforge.exam.entity.BasketComposeQuestion;
import io.github.kamill7779.qforge.exam.entity.BasketComposeSection;
import io.github.kamill7779.qforge.exam.entity.QuestionBasket;
import io.github.kamill7779.qforge.exam.exception.BusinessValidationException;
import io.github.kamill7779.qforge.exam.repository.BasketComposeQuestionRepository;
import io.github.kamill7779.qforge.exam.repository.BasketComposeRepository;
import io.github.kamill7779.qforge.exam.repository.BasketComposeSectionRepository;
import io.github.kamill7779.qforge.exam.repository.QuestionBasketRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 试题篮（购物车）服务 — 通过 QuestionCoreClient Feign 调用 question-core-service 校验题目。
 */
@Service
public class QuestionBasketService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBasketService.class);
    private static final String DEFAULT_SECTION_TITLE = "全部题目";

    private final QuestionBasketRepository basketRepository;
    private final BasketComposeRepository composeRepository;
    private final BasketComposeSectionRepository composeSectionRepository;
    private final BasketComposeQuestionRepository composeQuestionRepository;
    private final QuestionCoreClient questionCoreClient;
    private final ExamPaperInternalClient examPaperInternalClient;
    private final ExamCacheService examCacheService;
    private final QForgeComposeProperties composeProperties;

    public QuestionBasketService(
            QuestionBasketRepository basketRepository,
            QuestionCoreClient questionCoreClient,
            BasketComposeRepository composeRepository,
            BasketComposeSectionRepository composeSectionRepository,
            BasketComposeQuestionRepository composeQuestionRepository,
            ExamPaperInternalClient examPaperInternalClient,
            ExamCacheService examCacheService,
            QForgeComposeProperties composeProperties
    ) {
        this.basketRepository = basketRepository;
        this.questionCoreClient = questionCoreClient;
        this.composeRepository = composeRepository;
        this.composeSectionRepository = composeSectionRepository;
        this.composeQuestionRepository = composeQuestionRepository;
        this.examPaperInternalClient = examPaperInternalClient;
        this.examCacheService = examCacheService;
        this.composeProperties = composeProperties;
    }

    public List<BasketItemResponse> listItems(String requestUser) {
        return examCacheService.getBasketItems(requestUser, () -> {
            List<QuestionBasket> items = basketRepository.findByOwnerUser(requestUser);
            if (items.isEmpty()) {
                return List.of();
            }

            List<String> uuids = items.stream().map(QuestionBasket::getQuestionUuid).toList();

            Map<String, QuestionSummaryDTO> summaryMap;
            try {
                List<QuestionSummaryDTO> summaries = questionCoreClient.batchGetSummaries(String.join(",", uuids), requestUser);
                summaryMap = summaries.stream()
                        .collect(Collectors.toMap(QuestionSummaryDTO::questionUuid, s -> s));
            } catch (Exception e) {
                log.warn("Failed to fetch question summaries from question-core-service: {}", e.getMessage());
                summaryMap = Map.of();
            }

            Map<String, QuestionSummaryDTO> finalSummaryMap = summaryMap;
            return items.stream().map(item -> {
                QuestionSummaryDTO summary = finalSummaryMap.get(item.getQuestionUuid());
                return new BasketItemResponse(
                        item.getQuestionUuid(),
                        summary != null ? summary.stemText() : null,
                        summary != null ? summary.source() : null,
                        summary != null ? summary.difficulty() : null,
                        item.getAddedAt()
                );
            }).toList();
        });
    }

    public List<String> listUuids(String requestUser) {
        return examCacheService.getBasketUuids(
                requestUser,
                () -> basketRepository.findByOwnerUser(requestUser)
                        .stream()
                        .map(QuestionBasket::getQuestionUuid)
                        .toList()
        );
    }

    @Transactional
    public void addItem(String questionUuid, String requestUser) {
        Optional<QuestionSummaryDTO> questionSummary = questionCoreClient
            .batchGetSummaries(questionUuid, requestUser)
            .stream()
            .findFirst();
        if (questionSummary.isEmpty()) {
            throw new BusinessValidationException(
                    "QUESTION_NOT_FOUND", "题目不存在",
                    Map.of("questionUuid", questionUuid), HttpStatus.NOT_FOUND);
        }

        if (basketRepository.findByOwnerAndQuestionUuid(requestUser, questionUuid).isPresent()) {
            log.debug("Question {} already in basket for user {}", questionUuid, requestUser);
            return;
        }

        QuestionBasket item = new QuestionBasket();
        item.setOwnerUser(requestUser);
        item.setQuestionId(questionSummary.get().questionId());
        item.setQuestionUuid(questionUuid);
        basketRepository.insert(item);
        appendToComposeIfPresent(requestUser, questionSummary.get());
        examCacheService.evictBasket(requestUser);
        log.info("User [{}] added question {} to basket", requestUser, questionUuid);
    }

    @Transactional
    public void removeItem(String questionUuid, String requestUser) {
        basketRepository.findByOwnerAndQuestionUuid(requestUser, questionUuid)
                .ifPresent(item -> {
                    basketRepository.deleteById(item.getId());
                    removeFromComposeIfPresent(requestUser, questionUuid);
                    examCacheService.evictBasket(requestUser);
                    log.info("User [{}] removed question {} from basket", requestUser, questionUuid);
                });
    }

    @Transactional
    public boolean toggleItem(String questionUuid, String requestUser) {
        var existing = basketRepository.findByOwnerAndQuestionUuid(requestUser, questionUuid);
        if (existing.isPresent()) {
            basketRepository.deleteById(existing.get().getId());
            removeFromComposeIfPresent(requestUser, questionUuid);
            examCacheService.evictBasket(requestUser);
            log.info("User [{}] toggled question {} OUT of basket", requestUser, questionUuid);
            return false;
        } else {
            addItem(questionUuid, requestUser);
            return true;
        }
    }

    @Transactional
    public void clearBasket(String requestUser) {
        basketRepository.deleteByOwnerUser(requestUser);
        deleteCompose(requestUser);
        examCacheService.evictBasket(requestUser);
        log.info("User [{}] cleared basket", requestUser);
    }

    @Transactional
    public BasketComposeDetailResponse getCompose(String requestUser) {
        BasketCompose compose = ensureCompose(requestUser);
        reconcileComposeWithBasket(compose, requestUser);
        return toComposeDetail(reloadCompose(compose), requestUser);
    }

    @Transactional
    public BasketComposeDetailResponse updateComposeMeta(UpdateBasketComposeMetaRequest request, String requestUser) {
        BasketCompose compose = ensureCompose(requestUser);
        if (request.getTitle() != null) {
            compose.setTitle(request.getTitle());
        }
        if (request.getSubtitle() != null) {
            compose.setSubtitle(request.getSubtitle());
        }
        if (request.getDescription() != null) {
            compose.setDescription(request.getDescription());
        }
        if (request.getDurationMinutes() != null) {
            compose.setDurationMinutes(request.getDurationMinutes());
        }
        composeRepository.save(compose);
        return toComposeDetail(reloadCompose(compose), requestUser);
    }

    @Transactional
    public BasketComposeDetailResponse saveComposeContent(SaveBasketComposeContentRequest request, String requestUser) {
        BasketCompose compose = ensureCompose(requestUser);
        List<QuestionBasket> basketItems = basketRepository.findByOwnerUser(requestUser);
        List<String> basketUuids = basketItems.stream().map(QuestionBasket::getQuestionUuid).toList();
        Set<String> basketUuidSet = new HashSet<>(basketUuids);
        List<String> requestUuids = new ArrayList<>();
        List<SaveBasketComposeContentRequest.SectionPayload> sectionPayloads =
                request.getSections() != null ? request.getSections() : List.of();

        for (SaveBasketComposeContentRequest.SectionPayload section : sectionPayloads) {
            List<SaveBasketComposeContentRequest.QuestionPayload> questions =
                    section.getQuestions() != null ? section.getQuestions() : List.of();
            for (SaveBasketComposeContentRequest.QuestionPayload question : questions) {
                requestUuids.add(question.getQuestionUuid());
            }
        }

        if (requestUuids.size() != basketUuids.size() || !basketUuidSet.equals(new HashSet<>(requestUuids))) {
            throw new BusinessValidationException(
                    "COMPOSE_CONTENT_MISMATCH",
                    "组卷内容必须完整覆盖当前试题篮题目",
                    Map.of("basketCount", basketUuids.size(), "composeCount", requestUuids.size()),
                    HttpStatus.BAD_REQUEST
            );
        }

        List<BasketComposeSection> currentSections = composeSectionRepository.findByComposeId(compose.getId());
        List<Long> currentSectionIds = currentSections.stream().map(BasketComposeSection::getId).toList();
        composeQuestionRepository.deleteBySectionIds(currentSectionIds);
        composeSectionRepository.deleteByComposeId(compose.getId());

        for (int si = 0; si < sectionPayloads.size(); si++) {
            SaveBasketComposeContentRequest.SectionPayload payload = sectionPayloads.get(si);
            BasketComposeSection section = new BasketComposeSection();
            section.setSectionUuid(
                    payload.getSectionUuid() != null && !payload.getSectionUuid().isBlank()
                            ? payload.getSectionUuid()
                            : UUID.randomUUID().toString()
            );
            section.setComposeId(compose.getId());
            section.setTitle(payload.getTitle() != null && !payload.getTitle().isBlank()
                    ? payload.getTitle()
                    : "未命名大题");
            section.setDescription(payload.getDescription());
            section.setQuestionTypeCode(payload.getQuestionTypeCode());
            section.setDefaultScore(payload.getDefaultScore() != null
                    ? payload.getDefaultScore()
                    : composeProperties.getDefaultQuestionScore());
            section.setSortOrder(si);
            composeSectionRepository.save(section);

            List<SaveBasketComposeContentRequest.QuestionPayload> questions =
                    payload.getQuestions() != null ? payload.getQuestions() : List.of();
            for (int qi = 0; qi < questions.size(); qi++) {
                SaveBasketComposeContentRequest.QuestionPayload question = questions.get(qi);
                if (!basketUuidSet.contains(question.getQuestionUuid())) {
                    throw new BusinessValidationException(
                            "QUESTION_NOT_IN_BASKET",
                            "组卷中包含不在试题篮中的题目",
                            Map.of("questionUuid", question.getQuestionUuid()),
                            HttpStatus.BAD_REQUEST
                    );
                }
                BasketComposeQuestion composeQuestion = new BasketComposeQuestion();
                composeQuestion.setSectionId(section.getId());
                composeQuestion.setQuestionUuid(question.getQuestionUuid());
                composeQuestion.setSortOrder(qi);
                composeQuestion.setScore(question.getScore() != null ? question.getScore() : section.getDefaultScore());
                composeQuestion.setNote(question.getNote());
                composeQuestionRepository.save(composeQuestion);
            }
        }

        return toComposeDetail(reloadCompose(compose), requestUser);
    }

    @Transactional
    public ExamPaperDetailResponse confirmCompose(String requestUser) {
        BasketCompose compose = ensureCompose(requestUser);
        reconcileComposeWithBasket(compose, requestUser);

        List<BasketComposeSection> sections = composeSectionRepository.findByComposeId(compose.getId());
        List<Long> sectionIds = sections.stream().map(BasketComposeSection::getId).toList();
        List<BasketComposeQuestion> questions = composeQuestionRepository.findBySectionIds(sectionIds);
        if (questions.isEmpty()) {
            throw new BusinessValidationException(
                    "BASKET_EMPTY",
                    "试题篮为空，无法确认组卷",
                    Map.of(),
                    HttpStatus.BAD_REQUEST
            );
        }

        Map<Long, List<BasketComposeQuestion>> questionsBySection = questions.stream()
                .collect(Collectors.groupingBy(BasketComposeQuestion::getSectionId));

        List<BasketComposeSectionCreateRequest> internalSections = sections.stream().map(section ->
                new BasketComposeSectionCreateRequest(
                        section.getSectionUuid(),
                        section.getTitle(),
                        section.getDescription(),
                        section.getQuestionTypeCode(),
                        section.getDefaultScore(),
                        questionsBySection.getOrDefault(section.getId(), List.of()).stream()
                                .map(question -> new BasketComposeQuestionCreateRequest(
                                        question.getQuestionUuid(),
                                        question.getScore(),
                                        question.getNote()
                                ))
                                .toList()
                )
        ).toList();

        InternalExamPaperDetailDTO internalDetail = examPaperInternalClient.createFromBasketCompose(
                new BasketComposeCreateExamRequest(
                        compose.getTitle(),
                        compose.getSubtitle(),
                        compose.getDescription(),
                        compose.getDurationMinutes(),
                        internalSections
                ),
                requestUser
        );

        basketRepository.deleteByOwnerUser(requestUser);
        deleteCompose(requestUser);
        examCacheService.evictBasket(requestUser);
        return toExamDetailResponse(internalDetail);
    }

    private BasketCompose ensureCompose(String requestUser) {
        return composeRepository.findByOwnerUser(requestUser)
                .orElseGet(() -> initializeCompose(requestUser));
    }

    private BasketCompose initializeCompose(String requestUser) {
        BasketCompose compose = new BasketCompose();
        compose.setComposeUuid(UUID.randomUUID().toString());
        compose.setOwnerUser(requestUser);
        compose.setTitle("试题篮组卷");
        compose.setDurationMinutes(composeProperties.getDefaultDurationMinutes());
        composeRepository.save(compose);

        List<QuestionBasket> basketItems = basketRepository.findByOwnerUser(requestUser);
        if (!basketItems.isEmpty()) {
            BasketComposeSection section = createDefaultSection(compose.getId(), 0);
            for (int i = 0; i < basketItems.size(); i++) {
                QuestionBasket basketItem = basketItems.get(i);
                BasketComposeQuestion composeQuestion = new BasketComposeQuestion();
                composeQuestion.setSectionId(section.getId());
                composeQuestion.setQuestionUuid(basketItem.getQuestionUuid());
                composeQuestion.setSortOrder(i);
                composeQuestion.setScore(section.getDefaultScore());
                composeQuestionRepository.save(composeQuestion);
            }
        }
        return reloadCompose(compose);
    }

    private BasketCompose reloadCompose(BasketCompose compose) {
        if (compose.getOwnerUser() == null) {
            return compose;
        }
        return composeRepository.findByOwnerUser(compose.getOwnerUser()).orElse(compose);
    }

    private void appendToComposeIfPresent(String requestUser, QuestionSummaryDTO questionSummary) {
        composeRepository.findByOwnerUser(requestUser).ifPresent(compose -> {
            BasketComposeSection section = composeSectionRepository.findLastSection(compose.getId())
                    .orElseGet(() -> createDefaultSection(compose.getId(), 0));
            int nextSort = composeQuestionRepository.findBySectionIds(List.of(section.getId())).size();
            BasketComposeQuestion composeQuestion = new BasketComposeQuestion();
            composeQuestion.setSectionId(section.getId());
            composeQuestion.setQuestionUuid(questionSummary.questionUuid());
            composeQuestion.setSortOrder(nextSort);
            composeQuestion.setScore(section.getDefaultScore());
            composeQuestionRepository.save(composeQuestion);
        });
    }

    private void removeFromComposeIfPresent(String requestUser, String questionUuid) {
        composeRepository.findByOwnerUser(requestUser).ifPresent(compose -> {
            List<BasketComposeSection> sections = composeSectionRepository.findByComposeId(compose.getId());
            List<Long> sectionIds = sections.stream().map(BasketComposeSection::getId).toList();
            List<BasketComposeQuestion> questions = composeQuestionRepository.findBySectionIds(sectionIds);
            questions.stream()
                    .filter(question -> questionUuid.equals(question.getQuestionUuid()))
                    .forEach(question -> composeQuestionRepository.deleteById(question.getId()));
            reindexSectionQuestions(sectionIds);
        });
    }

    private void reconcileComposeWithBasket(BasketCompose compose, String requestUser) {
        List<QuestionBasket> basketItems = basketRepository.findByOwnerUser(requestUser);
        List<String> basketUuids = basketItems.stream().map(QuestionBasket::getQuestionUuid).toList();
        Set<String> basketUuidSet = new HashSet<>(basketUuids);
        List<BasketComposeSection> sections = composeSectionRepository.findByComposeId(compose.getId());

        if (sections.isEmpty()) {
            if (!basketItems.isEmpty()) {
                BasketComposeSection section = createDefaultSection(compose.getId(), 0);
                for (int i = 0; i < basketItems.size(); i++) {
                    BasketComposeQuestion question = new BasketComposeQuestion();
                    question.setSectionId(section.getId());
                    question.setQuestionUuid(basketItems.get(i).getQuestionUuid());
                    question.setSortOrder(i);
                    question.setScore(section.getDefaultScore());
                    composeQuestionRepository.save(question);
                }
            }
            return;
        }

        List<Long> sectionIds = sections.stream().map(BasketComposeSection::getId).toList();
        List<BasketComposeQuestion> questions = composeQuestionRepository.findBySectionIds(sectionIds);
        Set<String> composeUuids = questions.stream().map(BasketComposeQuestion::getQuestionUuid).collect(Collectors.toSet());

        for (BasketComposeQuestion question : questions) {
            if (!basketUuidSet.contains(question.getQuestionUuid())) {
                composeQuestionRepository.deleteById(question.getId());
            }
        }

        BasketComposeSection lastSection = sections.get(sections.size() - 1);
        if (lastSection == null && !basketItems.isEmpty()) {
            lastSection = createDefaultSection(compose.getId(), 0);
            sections = composeSectionRepository.findByComposeId(compose.getId());
            sectionIds = sections.stream().map(BasketComposeSection::getId).toList();
        }

        List<BasketComposeQuestion> lastSectionQuestions = lastSection != null
                ? composeQuestionRepository.findBySectionIds(List.of(lastSection.getId()))
                : List.of();
        int appendSort = lastSectionQuestions.size();
        for (String basketUuid : basketUuids) {
            if (!composeUuids.contains(basketUuid) && lastSection != null) {
                BasketComposeQuestion question = new BasketComposeQuestion();
                question.setSectionId(lastSection.getId());
                question.setQuestionUuid(basketUuid);
                question.setSortOrder(appendSort++);
                question.setScore(lastSection.getDefaultScore());
                composeQuestionRepository.save(question);
            }
        }

        reindexSectionQuestions(sectionIds);
    }

    private void reindexSectionQuestions(List<Long> sectionIds) {
        List<BasketComposeQuestion> questions = composeQuestionRepository.findBySectionIds(sectionIds);
        Map<Long, List<BasketComposeQuestion>> bySection = questions.stream()
                .collect(Collectors.groupingBy(BasketComposeQuestion::getSectionId));
        for (Map.Entry<Long, List<BasketComposeQuestion>> entry : bySection.entrySet()) {
            List<BasketComposeQuestion> sectionQuestions = entry.getValue();
            for (int i = 0; i < sectionQuestions.size(); i++) {
                BasketComposeQuestion question = sectionQuestions.get(i);
                if (question.getSortOrder() == null || question.getSortOrder() != i) {
                    question.setSortOrder(i);
                    composeQuestionRepository.save(question);
                }
            }
        }
    }

    private BasketComposeSection createDefaultSection(Long composeId, int sortOrder) {
        BasketComposeSection section = new BasketComposeSection();
        section.setSectionUuid(UUID.randomUUID().toString());
        section.setComposeId(composeId);
        section.setTitle(DEFAULT_SECTION_TITLE);
        section.setDefaultScore(composeProperties.getDefaultQuestionScore());
        section.setSortOrder(sortOrder);
        composeSectionRepository.save(section);
        return section;
    }

    private void deleteCompose(String requestUser) {
        composeRepository.findByOwnerUser(requestUser).ifPresent(compose -> composeRepository.deleteById(compose.getId()));
    }

    private BasketComposeDetailResponse toComposeDetail(BasketCompose compose, String requestUser) {
        List<BasketComposeSection> sections = composeSectionRepository.findByComposeId(compose.getId());
        List<Long> sectionIds = sections.stream().map(BasketComposeSection::getId).toList();
        List<BasketComposeQuestion> questions = composeQuestionRepository.findBySectionIds(sectionIds);
        Map<Long, List<BasketComposeQuestion>> questionsBySection = questions.stream()
                .collect(Collectors.groupingBy(BasketComposeQuestion::getSectionId));
        List<String> questionUuids = questions.stream().map(BasketComposeQuestion::getQuestionUuid).distinct().toList();
        Map<String, QuestionSummaryDTO> summaryMap = fetchSummaryMap(questionUuids, requestUser);

        List<BasketComposeSectionResponse> sectionResponses = sections.stream().map(section ->
                new BasketComposeSectionResponse(
                        section.getSectionUuid(),
                        section.getTitle(),
                        section.getDescription(),
                        section.getQuestionTypeCode(),
                        section.getDefaultScore(),
                        section.getSortOrder() != null ? section.getSortOrder() : 0,
                        questionsBySection.getOrDefault(section.getId(), List.of()).stream()
                                .map(question -> {
                                    QuestionSummaryDTO summary = summaryMap.get(question.getQuestionUuid());
                                    return new BasketComposeQuestionResponse(
                                            question.getQuestionUuid(),
                                            summary != null ? summary.stemText() : null,
                                            summary != null ? summary.source() : null,
                                            summary != null ? summary.difficulty() : null,
                                            question.getScore(),
                                            question.getSortOrder() != null ? question.getSortOrder() : 0,
                                            question.getNote()
                                    );
                                })
                                .toList()
                )
        ).toList();

        return new BasketComposeDetailResponse(
                compose.getComposeUuid(),
                compose.getTitle(),
                compose.getSubtitle(),
                compose.getDescription(),
                compose.getDurationMinutes(),
                sectionResponses,
                compose.getCreatedAt(),
                compose.getUpdatedAt()
        );
    }

    private Map<String, QuestionSummaryDTO> fetchSummaryMap(List<String> questionUuids, String requestUser) {
        if (questionUuids == null || questionUuids.isEmpty()) {
            return Map.of();
        }
        try {
            return questionCoreClient.batchGetSummaries(String.join(",", questionUuids), requestUser).stream()
                    .collect(Collectors.toMap(QuestionSummaryDTO::questionUuid, summary -> summary));
        } catch (Exception e) {
            log.warn("Failed to fetch question summaries from question-core-service: {}", e.getMessage());
            return Map.of();
        }
    }

    private ExamPaperDetailResponse toExamDetailResponse(InternalExamPaperDetailDTO detail) {
        return new ExamPaperDetailResponse(
                detail.paperUuid(),
                detail.title(),
                detail.subtitle(),
                detail.description(),
                detail.durationMinutes(),
                detail.totalScore(),
                detail.status(),
                detail.sections().stream().map(this::toExamSectionResponse).toList(),
                detail.createdAt(),
                detail.updatedAt()
        );
    }

    private ExamSectionResponse toExamSectionResponse(InternalExamSectionDTO section) {
        return new ExamSectionResponse(
                section.sectionUuid(),
                section.title(),
                section.description(),
                section.questionTypeCode(),
                section.defaultScore(),
                section.sortOrder(),
                section.questions().stream().map(this::toExamQuestionResponse).toList()
        );
    }

    private ExamQuestionResponse toExamQuestionResponse(InternalExamQuestionDTO question) {
        return new ExamQuestionResponse(
                question.questionUuid(),
                question.stemText(),
                question.score(),
                question.sortOrder(),
                question.note()
        );
    }
}
