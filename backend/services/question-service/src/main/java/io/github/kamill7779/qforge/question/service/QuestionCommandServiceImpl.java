package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.common.contract.AiAnalysisTaskCreatedEvent;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.question.client.OcrServiceClient;
import io.github.kamill7779.qforge.question.client.OcrServiceCreateTaskRequest;
import io.github.kamill7779.qforge.question.config.QForgeBusinessProperties;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.dto.AiTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.AddAnswerResponse;
import io.github.kamill7779.qforge.question.dto.AiTaskResponse;
import io.github.kamill7779.qforge.question.dto.AnswerOverviewResponse;
import io.github.kamill7779.qforge.question.dto.ApplyAiRecommendationRequest;
import io.github.kamill7779.qforge.question.dto.CreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.CreateQuestionRequest;
import io.github.kamill7779.qforge.question.dto.UpdateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskSubmitRequest;
import io.github.kamill7779.qforge.question.dto.QuestionMainTagResponse;
import io.github.kamill7779.qforge.question.dto.QuestionOverviewResponse;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import io.github.kamill7779.qforge.question.dto.UpdateDifficultyRequest;
import io.github.kamill7779.qforge.question.dto.InlineImageEntry;
import io.github.kamill7779.qforge.question.dto.QuestionAssetResponse;
import io.github.kamill7779.qforge.question.dto.UpdateStemRequest;
import io.github.kamill7779.qforge.question.dto.UpdateTagsRequest;
import io.github.kamill7779.qforge.question.entity.AnswerAsset;
import io.github.kamill7779.qforge.question.entity.QuestionAsset;
import io.github.kamill7779.qforge.question.entity.Answer;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionAiTask;
import io.github.kamill7779.qforge.question.entity.QuestionOcrTask;
import io.github.kamill7779.qforge.question.entity.QuestionTagRel;
import io.github.kamill7779.qforge.question.entity.Tag;
import io.github.kamill7779.qforge.question.entity.TagCategory;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.redis.TaskStateRedisService;
import io.github.kamill7779.qforge.question.repository.AnswerAssetRepository;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAiTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionOcrTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.repository.QuestionTagRelRepository;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import io.github.kamill7779.qforge.question.validation.StemXmlValidator;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QuestionCommandServiceImpl implements QuestionCommandService {

    private static final String CATEGORY_SECONDARY_CUSTOM = "SECONDARY_CUSTOM";
    private static final String TAG_SCOPE_USER = "USER";
    private static final String TAG_CODE_UNCATEGORIZED = "UNCATEGORIZED";
    private static final String ASSET_CACHE_PREFIX = "question:assets:";

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final AnswerAssetRepository answerAssetRepository;
    private final QuestionAssetRepository questionAssetRepository;
    private final QuestionOcrTaskRepository questionOcrTaskRepository;
    private final QuestionAiTaskRepository questionAiTaskRepository;
    private final TagRepository tagRepository;
    private final TagCategoryRepository tagCategoryRepository;
    private final QuestionTagRelRepository questionTagRelRepository;
    private final OcrServiceClient ocrServiceClient;
    private final StemXmlValidator stemXmlValidator;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;
    private final TaskStateRedisService taskStateRedisService;
    private final QForgeBusinessProperties businessProperties;
    private final StringRedisTemplate redis;

    public QuestionCommandServiceImpl(
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            AnswerAssetRepository answerAssetRepository,
            QuestionAssetRepository questionAssetRepository,
            QuestionOcrTaskRepository questionOcrTaskRepository,
            QuestionAiTaskRepository questionAiTaskRepository,
            TagRepository tagRepository,
            TagCategoryRepository tagCategoryRepository,
            QuestionTagRelRepository questionTagRelRepository,
            OcrServiceClient ocrServiceClient,
            StemXmlValidator stemXmlValidator,
            RabbitTemplate rabbitTemplate,
            ObjectMapper objectMapper,
            TaskStateRedisService taskStateRedisService,
            QForgeBusinessProperties businessProperties,
            StringRedisTemplate redis
    ) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.answerAssetRepository = answerAssetRepository;
        this.questionAssetRepository = questionAssetRepository;
        this.questionOcrTaskRepository = questionOcrTaskRepository;
        this.questionAiTaskRepository = questionAiTaskRepository;
        this.tagRepository = tagRepository;
        this.tagCategoryRepository = tagCategoryRepository;
        this.questionTagRelRepository = questionTagRelRepository;
        this.ocrServiceClient = ocrServiceClient;
        this.stemXmlValidator = stemXmlValidator;
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
        this.taskStateRedisService = taskStateRedisService;
        this.businessProperties = businessProperties;
        this.redis = redis;
    }

    @Override
    @Transactional
    public QuestionStatusResponse createDraft(CreateQuestionRequest request, String requestUser) {
        Question question = new Question();
        question.setQuestionUuid(UUID.randomUUID().toString());
        question.setOwnerUser(requestUser);
        if (request.getStemText() != null && !request.getStemText().isBlank()) {
            stemXmlValidator.validate(request.getStemText());
        }
        question.setStemText(request.getStemText());
        question.setStatus("DRAFT");
        question.setVisibility("PRIVATE");
        questionRepository.save(question);
        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    /** Validates stem XML then persists it, and syncs inline images atomically. */
    @Override
    @Transactional
    public QuestionStatusResponse updateStem(String questionUuid, UpdateStemRequest request, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        stemXmlValidator.validate(request.getStemXml());

        Map<String, InlineImageEntry> inlineImages = request.getInlineImages();
        if (inlineImages != null && !inlineImages.isEmpty()) {
            int maxImages = businessProperties.getMaxInlineImages();
            if (inlineImages.size() > maxImages) {
                throw new BusinessValidationException(
                        "ASSET_LIMIT_EXCEEDED",
                        "A question can have at most " + maxImages + " inline images",
                        Map.of("count", inlineImages.size(), "limit", maxImages)
                );
            }
            int maxBytes = businessProperties.getMaxImageBinaryBytes();
            for (Map.Entry<String, InlineImageEntry> entry : inlineImages.entrySet()) {
                String imageData = entry.getValue().imageData();
                if (imageData != null && !imageData.isBlank()) {
                    long approxBytes = (long) (imageData.length() * 3L / 4);
                    if (approxBytes > maxBytes) {
                        throw new BusinessValidationException(
                                "ASSET_SIZE_EXCEEDED",
                                "Image size must not exceed " + (maxBytes / 1024) + " KB",
                                Map.of("ref", entry.getKey(),
                                        "approxSizeBytes", approxBytes,
                                        "limitBytes", maxBytes)
                        );
                    }
                }
            }
            syncInlineImages(question, inlineImages);
        } else if (inlineImages != null) {
            // 客户端显式传空 map，表示清除所有图片
            questionAssetRepository.softDeleteByQuestionId(question.getId());
        }
        // inlineImages == null 表示客户端未传图片字段，跳过图片操作

        question.setStemText(request.getStemXml());
        questionRepository.save(question);
        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    /**
     * 同步单道题目的内联图片：upsert 传入的图片，软删除移除的图片。
     */
    private void syncInlineImages(Question question, Map<String, InlineImageEntry> inlineImages) {
        List<QuestionAsset> existing = questionAssetRepository.findByQuestionId(question.getId());
        Map<String, QuestionAsset> existingByRef = existing.stream()
                .filter(a -> a.getRefKey() != null)
                .collect(Collectors.toMap(QuestionAsset::getRefKey, a -> a, (a, b) -> a));
        Set<String> incomingRefs = inlineImages.keySet();

        for (Map.Entry<String, InlineImageEntry> entry : inlineImages.entrySet()) {
            String ref = entry.getKey();
            InlineImageEntry img = entry.getValue();
            QuestionAsset asset = existingByRef.get(ref);
            if (asset == null) {
                asset = new QuestionAsset();
                asset.setAssetUuid(UUID.randomUUID().toString());
                asset.setQuestionId(question.getId());
                asset.setRefKey(ref);
                asset.setAssetType("INLINE_IMAGE");
            }
            asset.setImageData(img.imageData());
            asset.setMimeType(img.mimeType() != null ? img.mimeType() : "image/png");
            asset.setFileName(ref);
            questionAssetRepository.save(asset);
        }

        for (QuestionAsset asset : existing) {
            if (asset.getRefKey() != null && !incomingRefs.contains(asset.getRefKey())) {
                questionAssetRepository.deleteById(asset.getId());
            }
        }
    }

    @Override
    @Transactional
    public AddAnswerResponse addAnswer(String questionUuid, CreateAnswerRequest request, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        validateAnswerHasContent(request.getLatexText(), questionUuid);
        Answer answer = saveOneAnswer(question, request.getLatexText());
        validateAndSyncAnswerImages(question, answer, request.getInlineImages());
        return new AddAnswerResponse(question.getQuestionUuid(), question.getStatus(), answer.getAnswerUuid());
    }

    @Override
    @Transactional
    public QuestionStatusResponse updateAnswer(String questionUuid, String answerUuid, UpdateAnswerRequest request, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        Answer answer = answerRepository.findByAnswerUuidAndQuestionId(answerUuid, question.getId())
                .orElseThrow(() -> new BusinessValidationException(
                        "ANSWER_NOT_FOUND",
                        "Answer not found",
                        Map.of("questionUuid", questionUuid, "answerUuid", answerUuid),
                        HttpStatus.NOT_FOUND
        ));
        answer.setLatexText(request.getLatexText());
        answerRepository.save(answer);
        validateAndSyncAnswerImages(question, answer, request.getInlineImages());
        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    @Override
    @Transactional
    public QuestionStatusResponse deleteAnswer(String questionUuid, String answerUuid, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        Answer answer = answerRepository.findByAnswerUuidAndQuestionId(answerUuid, question.getId())
                .orElseThrow(() -> new BusinessValidationException(
                        "ANSWER_NOT_FOUND",
                        "Answer not found",
                        Map.of("questionUuid", questionUuid, "answerUuid", answerUuid),
                        HttpStatus.NOT_FOUND
                ));
        long answerCount = answerRepository.countByQuestionId(question.getId());
        if (answerCount <= 1) {
            throw new BusinessValidationException(
                    "ANSWER_DELETE_LAST_NOT_ALLOWED",
                    "Cannot delete the last answer; at least one answer must remain",
                    Map.of("questionUuid", questionUuid, "answerUuid", answerUuid, "answerCount", answerCount)
            );
        }
        answerAssetRepository.deleteByAnswerId(answer.getId());
        answerRepository.deleteById(answer.getId());
        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    /** Submits an OCR task; bizType must be QUESTION_STEM or ANSWER_CONTENT. */
    @Override
    @Transactional
    public OcrTaskAcceptedResponse submitOcrTask(String questionUuid, OcrTaskSubmitRequest request, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        boolean answerContent = "ANSWER_CONTENT".equals(request.getBizType());
        if (answerContent) {
            boolean hasInProgress = questionOcrTaskRepository.existsByQuestionUuidAndBizTypeAndStatusIn(
                    questionUuid,
                    "ANSWER_CONTENT",
                    List.of("PENDING", "PROCESSING")
            );
            if (hasInProgress) {
                throw buildAnswerOcrTaskConflict(questionUuid);
            }
            boolean guardAcquired = taskStateRedisService.tryAcquireAnswerOcrGuard(questionUuid, requestUser);
            if (!guardAcquired) {
                throw buildAnswerOcrTaskConflict(questionUuid);
            }
        }

        OcrTaskAcceptedResponse response;
        try {
            response = ocrServiceClient.createTask(new OcrServiceCreateTaskRequest(
                    request.getBizType(),
                    questionUuid,
                    request.getImageBase64(),
                    requestUser
            ));
        } catch (RuntimeException ex) {
            if (answerContent) {
                taskStateRedisService.releaseAnswerOcrGuard(questionUuid);
            }
            throw ex;
        }

        // Redis 热状态先行写入，消费者可立即查到（解决 DB 行可见性竞态）
        taskStateRedisService.createOcrTask(response.taskUuid(), question.getQuestionUuid(),
                request.getBizType(), requestUser);

        QuestionOcrTask task = new QuestionOcrTask();
        task.setTaskUuid(response.taskUuid());
        task.setQuestionUuid(question.getQuestionUuid());
        task.setBizType(request.getBizType());
        task.setStatus(response.status());
        task.setRequestUser(requestUser);
        questionOcrTaskRepository.save(task);

        return response;
    }

    private BusinessValidationException buildAnswerOcrTaskConflict(String questionUuid) {
        return new BusinessValidationException(
                "OCR_TASK_CONFLICT",
                "Only one in-progress answer OCR task is allowed for the same question",
                Map.of("questionUuid", questionUuid, "bizType", "ANSWER_CONTENT"),
                HttpStatus.CONFLICT
        );
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
        questionAssetRepository.softDeleteByQuestionId(question.getId());
        answerRepository.deleteById(question.getId());
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
                            question.getDifficulty(),
                            answerCountMap.getOrDefault(question.getId(), 0L),
                            answersByQuestionId.getOrDefault(question.getId(), List.of()),
                            question.getUpdatedAt()
                    );
                })
                .toList();
    }

    @Override
    public List<QuestionAssetResponse> listAssets(String questionUuid, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        LinkedHashMap<String, QuestionAssetResponse> merged = new LinkedHashMap<>();

        for (QuestionAssetResponse stemAsset : loadStemAssets(questionUuid, question.getId())) {
            if (stemAsset.refKey() != null) {
                merged.put(stemAsset.refKey(), stemAsset);
            }
        }

        List<AnswerAsset> answerAssets = answerAssetRepository.findByQuestionId(question.getId());
        for (AnswerAsset asset : answerAssets) {
            if (asset.getRefKey() == null || asset.getImageData() == null) {
                continue;
            }
            merged.put(asset.getRefKey(), new QuestionAssetResponse(
                    asset.getAssetUuid(),
                    asset.getRefKey(),
                    asset.getImageData(),
                    asset.getMimeType()
            ));
        }

        Map<String, String> tempAnswerAssets = taskStateRedisService.getAnswerOcrAssets(questionUuid);
        for (Map.Entry<String, String> entry : tempAnswerAssets.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            merged.put(entry.getKey(), new QuestionAssetResponse(
                    "tmp-" + entry.getKey(),
                    entry.getKey(),
                    entry.getValue(),
                    "image/png"
            ));
        }

        return new ArrayList<>(merged.values());
    }

    private List<QuestionAssetResponse> loadStemAssets(String questionUuid, Long questionId) {
        try {
            String cached = redis.opsForValue().get(ASSET_CACHE_PREFIX + questionUuid);
            if (cached != null && !cached.isBlank()) {
                List<QuestionAssetResponse> result = objectMapper.readValue(
                        cached, new TypeReference<List<QuestionAssetResponse>>() {});
                if (result != null && !result.isEmpty()) {
                    return result;
                }
            }
        } catch (Exception ignored) {
            // Fallback to DB.
        }

        List<QuestionAsset> assets = questionAssetRepository.findByQuestionId(questionId);
        return assets.stream()
                .filter(a -> a.getRefKey() != null && a.getImageData() != null)
                .map(a -> new QuestionAssetResponse(
                        a.getAssetUuid(), a.getRefKey(), a.getImageData(), a.getMimeType()
                ))
                .toList();
    }

    private Answer saveOneAnswer(Question question, String latexText) {
        Answer answer = new Answer();
        answer.setAnswerUuid(UUID.randomUUID().toString());
        answer.setQuestionId(question.getId());
        answer.setAnswerType("LATEX_TEXT");
        answer.setLatexText(latexText);
        answer.setSortOrder((int) answerRepository.countByQuestionId(question.getId()) + 1);
        answer.setOfficial(false);
        answerRepository.save(answer);
        return answer;
    }

    /**
     * Reject answer text that is just an empty XML wrapper with no meaningful content.
     * E.g. {@code <answer version="1"><p></p></answer>} is structurally valid but useless.
     * Answers containing only images (no text) are still valid.
     */
    private void validateAnswerHasContent(String latexText, String questionUuid) {
        if (latexText == null || latexText.isBlank()) {
            throw new BusinessValidationException(
                    "ANSWER_EMPTY", "Answer text must not be empty",
                    Map.of("questionUuid", questionUuid));
        }
        // If it contains <image or <img tags, it has image content — allow
        if (latexText.contains("<image") || latexText.contains("<img")) {
            return;
        }
        // Strip all XML tags, then check if any non-whitespace remains
        String stripped = latexText.replaceAll("<[^>]+>", "").trim();
        if (stripped.isEmpty()) {
            throw new BusinessValidationException(
                    "ANSWER_EMPTY", "Answer text contains no meaningful content",
                    Map.of("questionUuid", questionUuid));
        }
    }

    /**
     * 校验答案内联图片大小/数量后同步到 q_answer_asset。
     */
    private void validateAndSyncAnswerImages(Question question, Answer answer,
                                             Map<String, InlineImageEntry> inlineImages) {
        if (inlineImages == null) {
            return; // null 表示客户端未传图片字段，跳过
        }
        if (inlineImages.isEmpty()) {
            // 显式传空 map → 清除该答案的所有图片
            answerAssetRepository.deleteByAnswerId(answer.getId());
            return;
        }
        int maxImages = businessProperties.getMaxInlineImages();
        if (inlineImages.size() > maxImages) {
            throw new BusinessValidationException(
                    "ASSET_LIMIT_EXCEEDED",
                    "An answer can have at most " + maxImages + " inline images",
                    Map.of("count", inlineImages.size(), "limit", maxImages)
            );
        }
        int maxBytes = businessProperties.getMaxImageBinaryBytes();
        for (Map.Entry<String, InlineImageEntry> entry : inlineImages.entrySet()) {
            String imageData = entry.getValue().imageData();
            if (imageData != null && !imageData.isBlank()) {
                long approxBytes = (long) (imageData.length() * 3L / 4);
                if (approxBytes > maxBytes) {
                    throw new BusinessValidationException(
                            "ASSET_SIZE_EXCEEDED",
                            "Image size must not exceed " + (maxBytes / 1024) + " KB",
                            Map.of("ref", entry.getKey(),
                                    "approxSizeBytes", approxBytes,
                                    "limitBytes", maxBytes)
                    );
                }
            }
        }
        syncAnswerImages(question, answer, inlineImages);
        taskStateRedisService.removeAnswerOcrAssets(question.getQuestionUuid(), inlineImages.keySet());
    }

    /**
     * 同步单个答案的内联图片：upsert 传入的图片，删除移除的图片。
     */
    private void syncAnswerImages(Question question, Answer answer,
                                  Map<String, InlineImageEntry> inlineImages) {
        List<AnswerAsset> existing = answerAssetRepository.findByAnswerId(answer.getId());
        Map<String, AnswerAsset> existingByRef = existing.stream()
                .filter(a -> a.getRefKey() != null)
                .collect(Collectors.toMap(AnswerAsset::getRefKey, a -> a, (a, b) -> a));
        Set<String> incomingRefs = inlineImages.keySet();

        for (Map.Entry<String, InlineImageEntry> entry : inlineImages.entrySet()) {
            String ref = entry.getKey();
            InlineImageEntry img = entry.getValue();
            AnswerAsset asset = existingByRef.get(ref);
            if (asset == null) {
                asset = new AnswerAsset();
                asset.setAssetUuid(UUID.randomUUID().toString());
                asset.setQuestionId(question.getId());
                asset.setAnswerId(answer.getId());
                asset.setRefKey(ref);
            }
            asset.setImageData(img.imageData());
            asset.setMimeType(img.mimeType() != null ? img.mimeType() : "image/png");
            answerAssetRepository.save(asset);
        }

        for (AnswerAsset asset : existing) {
            if (asset.getRefKey() != null && !incomingRefs.contains(asset.getRefKey())) {
                answerAssetRepository.deleteById(asset.getId());
            }
        }
    }

    /** Initialises default tags for a question if none have been set yet. */
    private void applyDefaultTagsIfAbsent(Question question, String requestUser) {
        if (questionTagRelRepository.countByQuestionId(question.getId()) > 0) {
            return;
        }

        List<TagCategory> mainCategories = tagCategoryRepository.findEnabledMainCategories();
        for (TagCategory category : mainCategories) {
            String categoryCode = category.getCategoryCode();
            Tag resolvedTag = tagRepository.findSystemTagByCategoryAndCode(categoryCode, TAG_CODE_UNCATEGORIZED)
                    .orElseGet(() -> {
                        List<Tag> options = tagRepository.findSystemTagsByCategory(categoryCode);
                        return options.isEmpty() ? null : options.get(0);
                    });
            if (resolvedTag != null) {
                saveQuestionTagRel(question.getId(), resolvedTag.getId(), categoryCode, requestUser);
            }
        }
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
        // Skip if relation already exists (idempotent)
        if (questionTagRelRepository.existsByQuestionIdAndTagId(questionId, tagId)) {
            return;
        }
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

    @Override
    @Transactional
    public QuestionStatusResponse updateTags(String questionUuid, UpdateTagsRequest request, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);

        // Delete ALL existing tag relations — full replace strategy
        questionTagRelRepository.deleteByQuestionId(question.getId());

        if (request.getTags() == null || request.getTags().isEmpty()) {
            return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
        }

        // Classify each tag: system tag (main category) vs free-text (secondary)
        Set<Long> insertedTagIds = new java.util.HashSet<>();
        for (String tagText : request.getTags()) {
            if (tagText == null || tagText.isBlank()) {
                continue;
            }

            // 1) Try as a known SYSTEM tag code (main categories like GRADE_7, FUNCTION, etc.)
            Optional<Tag> systemTag = tagRepository.findSystemTagByCode(tagText);
            if (systemTag.isPresent()) {
                Tag t = systemTag.get();
                if (insertedTagIds.add(t.getId())) {
                    saveQuestionTagRel(question.getId(), t.getId(), t.getCategoryCode(), requestUser);
                }
                continue;
            }

            // 2) Otherwise treat as secondary custom tag
            TagCategory secondaryCategory = tagCategoryRepository
                    .findEnabledByCode(CATEGORY_SECONDARY_CUSTOM).orElse(null);
            if (secondaryCategory == null) continue;

            String normalizedCode = toCustomTagCode(tagText);
            Tag tag = tagRepository
                    .findUserTagByCategoryAndCode(requestUser, CATEGORY_SECONDARY_CUSTOM, normalizedCode)
                    .orElseGet(() -> createUserCustomTag(requestUser, tagText));
            if (insertedTagIds.add(tag.getId())) {
                saveQuestionTagRel(question.getId(), tag.getId(), CATEGORY_SECONDARY_CUSTOM, requestUser);
            }
        }

        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    @Override
    @Transactional
    public QuestionStatusResponse updateDifficulty(String questionUuid, UpdateDifficultyRequest request, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);
        question.setDifficulty(request.getDifficulty());
        questionRepository.save(question);
        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    @Override
    @Transactional
    public AiTaskAcceptedResponse requestAiAnalysis(String questionUuid, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);

        if (question.getStemText() == null || question.getStemText().isBlank()) {
            throw new BusinessValidationException(
                    "AI_ANALYSIS_MISSING_STEM",
                    "Stem text is required for AI analysis",
                    Map.of("questionUuid", questionUuid)
            );
        }

        List<Answer> answers = answerRepository.findByQuestionIds(List.of(question.getId()));

        if (answers.isEmpty()) {
            throw new BusinessValidationException(
                    "AI_ANALYSIS_MISSING_ANSWER",
                    "At least one answer is required for AI analysis",
                    Map.of("questionUuid", questionUuid)
            );
        }

        String taskUuid = UUID.randomUUID().toString();

        // Redis 热状态先行写入，消费者可立即查到
        taskStateRedisService.createAiTask(taskUuid, questionUuid, requestUser);

        // Persist PENDING row
        QuestionAiTask aiTask = new QuestionAiTask();
        aiTask.setTaskUuid(taskUuid);
        aiTask.setQuestionUuid(questionUuid);
        aiTask.setStatus("PENDING");
        aiTask.setRequestUser(requestUser);
        questionAiTaskRepository.insert(aiTask);

        List<String> answerTexts = answers.stream()
                .map(Answer::getLatexText)
                .toList();

        AiAnalysisTaskCreatedEvent event = new AiAnalysisTaskCreatedEvent(
                taskUuid,
                questionUuid,
                requestUser,
                question.getStemText(),
                answerTexts
        );

        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.AI_EXCHANGE,
                RabbitTopologyConfig.ROUTING_AI_ANALYSIS_CREATED,
                event
        );

        return new AiTaskAcceptedResponse(taskUuid);
    }

    @Override
    @Transactional
    public QuestionStatusResponse applyAiRecommendation(String questionUuid, String taskUuid,
            ApplyAiRecommendationRequest request, String requestUser) {
        Question question = findQuestionOwnedByUser(questionUuid, requestUser);

        // Redis-first 状态校验：优先信任热缓存，TTL 过期后回退 DB
        String redisStatus = taskStateRedisService.getAiTask(taskUuid)
                .map(m -> (String) m.get("status"))
                .orElse(null);

        // 加载 DB 实体（后续 updateById(APPLIED) 必须使用）
        QuestionAiTask aiTask = questionAiTaskRepository.findByTaskUuid(taskUuid)
                .orElseThrow(() -> new BusinessValidationException(
                        "AI_TASK_NOT_FOUND", "AI task not found",
                        Map.of("taskUuid", taskUuid)));

        // 以 Redis 为准；若 Redis 已过期则回退到 DB 状态
        String effectiveStatus = (redisStatus != null) ? redisStatus : aiTask.getStatus();
        if (!"SUCCESS".equals(effectiveStatus)) {
            throw new BusinessValidationException(
                    "AI_TASK_NOT_SUCCESS", "AI task is not in SUCCESS status",
                    Map.of("taskUuid", taskUuid, "status", effectiveStatus));
        }

        // Apply tags
        if (request.getTags() != null && !request.getTags().isEmpty()) {
            UpdateTagsRequest tagsReq = new UpdateTagsRequest();
            tagsReq.setTags(request.getTags());
            updateTags(questionUuid, tagsReq, requestUser);
        }

        // Apply difficulty
        if (request.getDifficulty() != null) {
            question.setDifficulty(request.getDifficulty());
            questionRepository.save(question);
        }

        // Mark as APPLIED
        aiTask.setStatus("APPLIED");
        aiTask.setAppliedAt(LocalDateTime.now());
        questionAiTaskRepository.updateById(aiTask);

        return new QuestionStatusResponse(question.getQuestionUuid(), question.getStatus());
    }

    @Override
    public List<AiTaskResponse> listAiTasks(String questionUuid, String requestUser) {
        findQuestionOwnedByUser(questionUuid, requestUser);
        List<QuestionAiTask> tasks = questionAiTaskRepository.findByQuestionUuid(questionUuid);
        return tasks.stream().map(this::toAiTaskResponse).toList();
    }

    private AiTaskResponse toAiTaskResponse(QuestionAiTask task) {
        List<String> tags = List.of();
        if (task.getSuggestedTags() != null && !task.getSuggestedTags().isBlank()) {
            try {
                tags = objectMapper.readValue(task.getSuggestedTags(), new TypeReference<List<String>>() {});
            } catch (Exception ignored) {}
        }
        return new AiTaskResponse(
                task.getTaskUuid(),
                task.getQuestionUuid(),
                task.getStatus(),
                tags,
                task.getSuggestedDifficulty(),
                task.getReasoning(),
                task.getErrorMsg(),
                task.getAppliedAt(),
                null // createdAt not mapped in entity for now
        );
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
