package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.question.client.OcrServiceClient;
import io.github.kamill7779.qforge.question.client.OcrServiceCreateTaskRequest;
import io.github.kamill7779.qforge.question.dto.AnswerOverviewResponse;
import io.github.kamill7779.qforge.question.dto.CreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.CreateQuestionRequest;
import io.github.kamill7779.qforge.question.dto.MainTagSelectionRequest;
import io.github.kamill7779.qforge.question.dto.OcrConfirmRequest;
import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskSubmitRequest;
import io.github.kamill7779.qforge.question.dto.QuestionMainTagResponse;
import io.github.kamill7779.qforge.question.dto.QuestionOverviewResponse;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import io.github.kamill7779.qforge.question.entity.Answer;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionOcrTask;
import io.github.kamill7779.qforge.question.entity.QuestionTagRel;
import io.github.kamill7779.qforge.question.entity.Tag;
import io.github.kamill7779.qforge.question.entity.TagCategory;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionOcrTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.repository.QuestionTagRelRepository;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionCommandServiceImpl implements QuestionCommandService {

    private static final String CATEGORY_SECONDARY_CUSTOM = "SECONDARY_CUSTOM";
    private static final String TAG_SCOPE_USER = "USER";
    private static final String TAG_CODE_UNCATEGORIZED = "UNCATEGORIZED";

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionOcrTaskRepository questionOcrTaskRepository;
    private final TagRepository tagRepository;
    private final TagCategoryRepository tagCategoryRepository;
    private final QuestionTagRelRepository questionTagRelRepository;
    private final OcrServiceClient ocrServiceClient;

    public QuestionCommandServiceImpl(
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            QuestionOcrTaskRepository questionOcrTaskRepository,
            TagRepository tagRepository,
            TagCategoryRepository tagCategoryRepository,
            QuestionTagRelRepository questionTagRelRepository,
            OcrServiceClient ocrServiceClient
    ) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.questionOcrTaskRepository = questionOcrTaskRepository;
        this.tagRepository = tagRepository;
        this.tagCategoryRepository = tagCategoryRepository;
        this.questionTagRelRepository = questionTagRelRepository;
        this.ocrServiceClient = ocrServiceClient;
    }

    @Override
    @Transactional
    public QuestionStatusResponse createDraft(CreateQuestionRequest request, String requestUser) {
        Question question = new Question();
        question.setQuestionUuid(UUID.randomUUID().toString());
        question.setOwnerUser(requestUser);
        question.setStemText(request.getStemText());
        question.setStatus("DRAFT");
        question.setVisibility("PRIVATE");
        questionRepository.save(question);
        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    @Override
    @Transactional
    public QuestionStatusResponse addAnswer(String questionUuid, CreateAnswerRequest request, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        Answer answer = new Answer();
        answer.setAnswerUuid(UUID.randomUUID().toString());
        answer.setQuestionId(question.getId());
        answer.setAnswerType("LATEX_TEXT");
        answer.setLatexText(request.getLatexText());
        answer.setSortOrder((int) answerRepository.countByQuestionId(question.getId()) + 1);
        answer.setOfficial(false);
        answerRepository.save(answer);
        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    @Override
    @Transactional
    public OcrTaskAcceptedResponse submitQuestionStemOcr(String questionUuid, OcrTaskSubmitRequest request, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        OcrTaskAcceptedResponse response = ocrServiceClient.createTask(new OcrServiceCreateTaskRequest(
                "QUESTION_STEM",
                questionUuid,
                request.getImageBase64(),
                requestUser
        ));

        QuestionOcrTask task = new QuestionOcrTask();
        task.setTaskUuid(response.taskUuid());
        task.setQuestionUuid(question.getQuestionUuid());
        task.setBizType("QUESTION_STEM");
        task.setStatus(response.status());
        task.setRequestUser(requestUser);
        questionOcrTaskRepository.save(task);

        return response;
    }

    @Override
    @Transactional
    public QuestionStatusResponse confirmOcrTask(String taskUuid, OcrConfirmRequest request, String requestUser) {
        QuestionOcrTask task = questionOcrTaskRepository.findByTaskUuid(taskUuid)
                .orElseThrow(() -> new BusinessValidationException(
                        "OCR_TASK_NOT_FOUND",
                        "OCR task not found",
                        Map.of("taskUuid", taskUuid),
                        HttpStatus.NOT_FOUND
                ));
        if (!task.getRequestUser().equals(requestUser)) {
            throw new BusinessValidationException(
                    "OCR_TASK_FORBIDDEN",
                    "Current user has no permission for this OCR task",
                    Map.of("taskUuid", taskUuid, "requestUser", requestUser),
                    HttpStatus.FORBIDDEN
            );
        }

        Question question = findQuestionOwnedByUser(task.getQuestionUuid(), requestUser);
        if ("QUESTION_STEM".equals(task.getBizType())) {
            question.setStemText(request.getConfirmedText());
            questionRepository.save(question);
            applyStemTagsOnFirstConfirm(question, request, requestUser);
        }

        task.setStatus("CONFIRMED");
        task.setConfirmedText(request.getConfirmedText());
        questionOcrTaskRepository.save(task);
        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    @Override
    @Transactional
    public QuestionStatusResponse completeQuestion(String questionUuid, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        long answerCount = answerRepository.countByQuestionId(question.getId());
        boolean missingStem = question.getStemText() == null || question.getStemText().isBlank();
        boolean missingAnswer = answerCount <= 0;
        if (missingStem || missingAnswer) {
            ArrayList<String> missingFields = new ArrayList<>();
            if (missingStem) {
                missingFields.add("stemText");
            }
            if (missingAnswer) {
                missingFields.add("answers");
            }
            throw new BusinessValidationException(
                    "QUESTION_COMPLETE_VALIDATION_FAILED",
                    "stem_text is required and at least one answer is required",
                    Map.of("missingFields", missingFields)
            );
        }

        question.setStatus("READY");
        questionRepository.save(question);
        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    @Override
    @Transactional
    public void deleteDraftQuestion(String questionUuid, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        long answerCount = answerRepository.countByQuestionId(question.getId());
        boolean draftStatus = "DRAFT".equals(question.getStatus());
        if (!draftStatus || answerCount > 0) {
            throw new BusinessValidationException(
                    "QUESTION_DELETE_NOT_ALLOWED",
                    "Only draft question without answers can be deleted",
                    Map.of(
                            "questionUuid", questionUuid,
                            "status", question.getStatus(),
                            "answerCount", answerCount
                    )
            );
        }

        questionOcrTaskRepository.deleteByQuestionUuid(question.getQuestionUuid());
        questionRepository.deleteById(question.getId());
    }

    @Override
    public List<QuestionOverviewResponse> listUserQuestions(String requestUser) {
        List<Question> questions = questionRepository.findAllByOwnerUser(requestUser);
        if (questions.isEmpty()) {
            return List.of();
        }

        List<Long> questionIds = questions.stream().map(Question::getId).toList();
        Map<Long, Long> answerCountMap = answerRepository.countByQuestionIds(questionIds);
        Map<Long, TagSnapshot> tagSnapshotMap = loadTagSnapshotByQuestionIds(questionIds);

        List<Answer> allAnswers = answerRepository.findByQuestionIds(questionIds);
        Map<Long, List<AnswerOverviewResponse>> answersByQuestionId = new HashMap<>();
        for (Answer answer : allAnswers) {
            answersByQuestionId
                    .computeIfAbsent(answer.getQuestionId(), ignored -> new ArrayList<>())
                    .add(new AnswerOverviewResponse(
                            answer.getAnswerUuid(),
                            answer.getAnswerType(),
                            answer.getLatexText(),
                            answer.getSortOrder(),
                            answer.isOfficial()
                    ));
        }

        return questions.stream()
                .map(question -> {
                    TagSnapshot tagSnapshot = tagSnapshotMap.getOrDefault(question.getId(), TagSnapshot.defaultValue(List.of()));
                    return new QuestionOverviewResponse(
                            question.getQuestionUuid(),
                            question.getStatus(),
                            question.getStemText(),
                            tagSnapshot.mainTags(),
                            tagSnapshot.secondaryTags(),
                            answerCountMap.getOrDefault(question.getId(), 0L),
                            answersByQuestionId.getOrDefault(question.getId(), List.of()),
                            question.getUpdatedAt()
                    );
                })
                .toList();
    }

    private void applyStemTagsOnFirstConfirm(Question question, OcrConfirmRequest request, String requestUser) {
        if (questionTagRelRepository.countByQuestionId(question.getId()) > 0) {
            return;
        }

        List<TagCategory> mainCategories = tagCategoryRepository.findEnabledMainCategories();
        Map<String, String> requestedMainTags = normalizeMainTagSelection(request.getMainTags());

        for (TagCategory category : mainCategories) {
            String categoryCode = category.getCategoryCode();
            String tagCode = requestedMainTags.getOrDefault(categoryCode, TAG_CODE_UNCATEGORIZED);
            Tag resolvedTag = tagRepository.findSystemTagByCategoryAndCode(categoryCode, tagCode)
                    .orElseThrow(() -> new BusinessValidationException(
                            "QUESTION_TAG_NOT_ALLOWED",
                            "Main tag code is not in allowed range",
                            Map.of("categoryCode", categoryCode, "tagCode", tagCode)
                    ));
            saveQuestionTagRel(question.getId(), resolvedTag.getId(), categoryCode, requestUser);
        }

        for (String secondaryTagText : parseSecondaryTags(request.getSecondaryTagsText())) {
            String code = toCustomTagCode(secondaryTagText);
            Tag customTag = tagRepository.findUserTagByCategoryAndCode(requestUser, CATEGORY_SECONDARY_CUSTOM, code)
                    .orElseGet(() -> createUserCustomTag(requestUser, secondaryTagText));
            saveQuestionTagRel(question.getId(), customTag.getId(), CATEGORY_SECONDARY_CUSTOM, requestUser);
        }
    }

    private Map<String, String> normalizeMainTagSelection(List<MainTagSelectionRequest> rawSelections) {
        if (rawSelections == null || rawSelections.isEmpty()) {
            return Map.of();
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (MainTagSelectionRequest selection : rawSelections) {
            if (selection == null) {
                continue;
            }
            String categoryCode = safeUpper(selection.getCategoryCode());
            String tagCode = safeUpper(selection.getTagCode());
            if (categoryCode.isBlank() || tagCode.isBlank()) {
                continue;
            }
            result.put(categoryCode, tagCode);
        }
        return result;
    }

    private String safeUpper(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private List<String> parseSecondaryTags(String secondaryTagsText) {
        if (secondaryTagsText == null || secondaryTagsText.isBlank()) {
            return List.of();
        }

        String[] raw = secondaryTagsText.trim().split("\\s+");
        LinkedHashSet<String> deduped = new LinkedHashSet<>();
        for (String token : raw) {
            String normalized = token == null ? "" : token.trim();
            if (normalized.isBlank()) {
                continue;
            }
            if (normalized.length() > 64) {
                throw new BusinessValidationException(
                        "QUESTION_SECONDARY_TAG_TOO_LONG",
                        "Secondary tag length must be <= 64",
                        Map.of("tag", normalized)
                );
            }
            deduped.add(normalized);
        }
        return List.copyOf(deduped);
    }

    private String toCustomTagCode(String tagText) {
        return tagText.trim().toLowerCase(Locale.ROOT);
    }

    private Tag createUserCustomTag(String requestUser, String tagText) {
        Tag tag = new Tag();
        tag.setTagUuid(UUID.randomUUID().toString());
        tag.setCategoryCode(CATEGORY_SECONDARY_CUSTOM);
        tag.setTagCode(toCustomTagCode(tagText));
        tag.setTagName(tagText.trim());
        tag.setScope(TAG_SCOPE_USER);
        tag.setOwnerUser(requestUser);
        return tagRepository.save(tag);
    }

    private void saveQuestionTagRel(Long questionId, Long tagId, String categoryCode, String createdBy) {
        QuestionTagRel rel = new QuestionTagRel();
        rel.setQuestionId(questionId);
        rel.setTagId(tagId);
        rel.setCategoryCode(categoryCode);
        rel.setCreatedBy(createdBy);
        questionTagRelRepository.save(rel);
    }

    private Map<Long, TagSnapshot> loadTagSnapshotByQuestionIds(List<Long> questionIds) {
        List<TagCategory> mainCategories = tagCategoryRepository.findEnabledMainCategories();
        Map<String, TagCategory> mainCategoryMap = mainCategories.stream()
                .collect(Collectors.toMap(TagCategory::getCategoryCode, item -> item));

        Map<String, Tag> defaultMainTagByCategory = buildDefaultMainTagMap(mainCategories);
        List<QuestionMainTagResponse> defaultMainTagResponses = mainCategories.stream()
                .map(category -> toMainTagResponse(category, defaultMainTagByCategory.get(category.getCategoryCode())))
                .toList();

        List<QuestionTagRel> rels = questionTagRelRepository.findByQuestionIds(questionIds);
        if (rels.isEmpty()) {
            return questionIds.stream()
                    .collect(Collectors.toMap(id -> id, ignored -> TagSnapshot.defaultValue(defaultMainTagResponses)));
        }

        Set<Long> tagIds = rels.stream().map(QuestionTagRel::getTagId).collect(Collectors.toSet());
        Map<Long, Tag> tagMap = tagRepository.findByIds(new ArrayList<>(tagIds)).stream()
                .collect(Collectors.toMap(Tag::getId, item -> item));

        Map<Long, Map<String, QuestionMainTagResponse>> mainTagMapByQuestion = new HashMap<>();
        Map<Long, List<String>> secondaryTagByQuestion = new HashMap<>();

        for (Long questionId : questionIds) {
            Map<String, QuestionMainTagResponse> defaultPerQuestion = new LinkedHashMap<>();
            for (TagCategory category : mainCategories) {
                defaultPerQuestion.put(
                        category.getCategoryCode(),
                        toMainTagResponse(category, defaultMainTagByCategory.get(category.getCategoryCode()))
                );
            }
            mainTagMapByQuestion.put(questionId, defaultPerQuestion);
        }

        for (QuestionTagRel rel : rels) {
            Tag tag = tagMap.get(rel.getTagId());
            if (tag == null) {
                continue;
            }

            if (CATEGORY_SECONDARY_CUSTOM.equals(rel.getCategoryCode())) {
                secondaryTagByQuestion.computeIfAbsent(rel.getQuestionId(), ignored -> new ArrayList<>()).add(tag.getTagName());
                continue;
            }

            TagCategory category = mainCategoryMap.get(rel.getCategoryCode());
            if (category == null) {
                continue;
            }

            Map<String, QuestionMainTagResponse> current = mainTagMapByQuestion.get(rel.getQuestionId());
            if (current == null) {
                continue;
            }
            current.put(category.getCategoryCode(), toMainTagResponse(category, tag));
        }

        Map<Long, TagSnapshot> result = new HashMap<>();
        for (Long questionId : questionIds) {
            List<QuestionMainTagResponse> mainTags = new ArrayList<>();
            Map<String, QuestionMainTagResponse> current = mainTagMapByQuestion.get(questionId);
            if (current != null) {
                for (TagCategory category : mainCategories) {
                    mainTags.add(current.get(category.getCategoryCode()));
                }
            }
            result.put(
                    questionId,
                    new TagSnapshot(
                            mainTags.isEmpty() ? defaultMainTagResponses : mainTags,
                            secondaryTagByQuestion.getOrDefault(questionId, List.of())
                    )
            );
        }
        return result;
    }

    private Map<String, Tag> buildDefaultMainTagMap(List<TagCategory> mainCategories) {
        Map<String, Tag> result = new HashMap<>();
        for (TagCategory category : mainCategories) {
            Tag defaultTag = tagRepository.findSystemTagByCategoryAndCode(category.getCategoryCode(), TAG_CODE_UNCATEGORIZED)
                    .orElseGet(() -> {
                        List<Tag> options = tagRepository.findSystemTagsByCategory(category.getCategoryCode());
                        return options.isEmpty() ? null : options.get(0);
                    });
            result.put(category.getCategoryCode(), defaultTag);
        }
        return result;
    }

    private QuestionMainTagResponse toMainTagResponse(TagCategory category, Tag tag) {
        String tagCode = tag == null ? TAG_CODE_UNCATEGORIZED : tag.getTagCode();
        String tagName = tag == null ? "Uncategorized" : tag.getTagName();
        return new QuestionMainTagResponse(
                category.getCategoryCode(),
                category.getCategoryName(),
                tagCode,
                tagName
        );
    }

    private Question findQuestionOwnedByUser(String questionUuid, String requestUser) {
        return questionRepository.findByQuestionUuidAndOwnerUser(questionUuid, requestUser)
                .orElseThrow(() -> new BusinessValidationException(
                        "QUESTION_NOT_FOUND",
                        "Question not found",
                        Map.of("questionUuid", questionUuid, "requestUser", requestUser),
                        HttpStatus.NOT_FOUND
                ));
    }

    private record TagSnapshot(
            List<QuestionMainTagResponse> mainTags,
            List<String> secondaryTags
    ) {
        static TagSnapshot defaultValue(List<QuestionMainTagResponse> defaultMainTags) {
            return new TagSnapshot(defaultMainTags, List.of());
        }
    }
}
