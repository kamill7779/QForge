package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.internal.api.CreateQuestionFromGaokaoRequest;
import io.github.kamill7779.qforge.internal.api.CreateQuestionFromGaokaoResponse;
import io.github.kamill7779.qforge.question.entity.Answer;
import io.github.kamill7779.qforge.question.entity.AnswerAsset;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionAsset;
import io.github.kamill7779.qforge.question.repository.AnswerAssetRepository;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GaokaoQuestionCreateService {

    private static final Logger log = LoggerFactory.getLogger(GaokaoQuestionCreateService.class);

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionAssetRepository questionAssetRepository;
    private final AnswerAssetRepository answerAssetRepository;
    private final QuestionTagAssignmentService questionTagAssignmentService;
    private final QuestionSummaryQueryService questionSummaryQueryService;

    public GaokaoQuestionCreateService(
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            QuestionAssetRepository questionAssetRepository,
            AnswerAssetRepository answerAssetRepository,
            QuestionTagAssignmentService questionTagAssignmentService,
            QuestionSummaryQueryService questionSummaryQueryService
    ) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.questionAssetRepository = questionAssetRepository;
        this.answerAssetRepository = answerAssetRepository;
        this.questionTagAssignmentService = questionTagAssignmentService;
        this.questionSummaryQueryService = questionSummaryQueryService;
    }

    @Transactional
    public CreateQuestionFromGaokaoResponse create(CreateQuestionFromGaokaoRequest request) {
        CreateQuestionFromGaokaoResponse response = new CreateQuestionFromGaokaoResponse();
        try {
            String questionUuid = UUID.randomUUID().toString();
            Question question = new Question();
            question.setQuestionUuid(questionUuid);
            question.setOwnerUser(request.getOwnerUser());
            question.setStemText(firstNonBlank(request.getStemXml(), request.getStemText()));
            question.setStatus("READY");
            question.setVisibility("PRIVATE");
            question.setDifficulty(request.getDifficulty());
            question.setSource(firstNonBlank(request.getSource(), "GAOKAO_CORPUS"));
            question.setDeleted(false);
            question.setCreatedAt(LocalDateTime.now());
            question.setUpdatedAt(LocalDateTime.now());
            questionRepository.save(question);

            saveStemAssets(question, request.getStemAssets());
            saveOfficialAnswer(question, request);
            questionTagAssignmentService.replaceTags(question, mergeTags(request), request.getOwnerUser());
            questionSummaryQueryService.evict(request.getOwnerUser(), questionUuid);

            response.setSuccess(true);
            response.setQuestionId(question.getId());
            response.setQuestionUuid(questionUuid);
            log.info("Question created from gaokao: questionUuid={}, ownerUser={}", questionUuid, request.getOwnerUser());
        } catch (Exception ex) {
            response.setSuccess(false);
            response.setErrorMessage(ex.getMessage());
            log.error("Failed to create question from gaokao", ex);
        }
        return response;
    }

    private List<String> mergeTags(CreateQuestionFromGaokaoRequest request) {
        List<String> tags = new ArrayList<>();
        if (request.getMainTags() != null) {
            for (CreateQuestionFromGaokaoRequest.TagEntry entry : request.getMainTags()) {
                if (entry != null && entry.getTagCode() != null && !entry.getTagCode().isBlank()) {
                    tags.add(entry.getTagCode());
                }
            }
        }
        if (request.getSecondaryTags() != null) {
            for (String tag : request.getSecondaryTags()) {
                if (tag != null && !tag.isBlank()) {
                    tags.add(tag);
                }
            }
        }
        return tags;
    }

    private void saveStemAssets(Question question, List<CreateQuestionFromGaokaoRequest.AssetEntry> assets) {
        if (assets == null || assets.isEmpty()) {
            return;
        }
        for (CreateQuestionFromGaokaoRequest.AssetEntry entry : assets) {
            if (entry == null || entry.getStorageRef() == null || entry.getStorageRef().isBlank()) {
                continue;
            }
            QuestionAsset asset = new QuestionAsset();
            asset.setAssetUuid(UUID.randomUUID().toString());
            asset.setQuestionId(question.getId());
            asset.setAssetType(firstNonBlank(entry.getAssetType(), "INLINE_IMAGE"));
            asset.setRefKey(firstNonBlank(entry.getRefKey(), "img-" + UUID.randomUUID()));
            asset.setImageData(loadAssetData(entry.getStorageRef()));
            asset.setMimeType(detectMimeType(entry.getStorageRef()));
            asset.setDeleted(false);
            asset.setCreatedAt(LocalDateTime.now());
            asset.setUpdatedAt(LocalDateTime.now());
            questionAssetRepository.save(asset);
        }
    }

    private void saveOfficialAnswer(Question question, CreateQuestionFromGaokaoRequest request) {
        if ((request.getAnswerXml() == null || request.getAnswerXml().isBlank())
                && (request.getAnswerText() == null || request.getAnswerText().isBlank())) {
            return;
        }

        Answer answer = new Answer();
        answer.setAnswerUuid(UUID.randomUUID().toString());
        answer.setQuestionId(question.getId());
        answer.setLatexText(firstNonBlank(request.getAnswerXml(), request.getAnswerText()));
        answer.setAnswerType("SOLUTION");
        answer.setSortOrder(0);
        answer.setOfficial(true);
        answer.setDeleted(false);
        answer.setCreatedAt(LocalDateTime.now());
        answer.setUpdatedAt(LocalDateTime.now());
        answerRepository.save(answer);

        if (request.getAnswerAssets() == null || request.getAnswerAssets().isEmpty()) {
            return;
        }
        for (CreateQuestionFromGaokaoRequest.AssetEntry entry : request.getAnswerAssets()) {
            if (entry == null || entry.getStorageRef() == null || entry.getStorageRef().isBlank()) {
                continue;
            }
            AnswerAsset asset = new AnswerAsset();
            asset.setAssetUuid(UUID.randomUUID().toString());
            asset.setQuestionId(question.getId());
            asset.setAnswerId(answer.getId());
            asset.setRefKey(firstNonBlank(entry.getRefKey(), "ans-img-" + UUID.randomUUID()));
            asset.setImageData(loadAssetData(entry.getStorageRef()));
            asset.setMimeType(detectMimeType(entry.getStorageRef()));
            asset.setDeleted(false);
            asset.setCreatedAt(LocalDateTime.now());
            asset.setUpdatedAt(LocalDateTime.now());
            answerAssetRepository.save(asset);
        }
    }

    private String loadAssetData(String storageRef) {
        if (storageRef.startsWith("data:")) {
            int index = storageRef.indexOf("base64,");
            return index >= 0 ? storageRef.substring(index + 7) : storageRef;
        }
        Path path = Path.of(storageRef);
        if (!Files.exists(path)) {
            return storageRef;
        }
        try {
            return Base64.getEncoder().encodeToString(Files.readAllBytes(path));
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read asset file: " + storageRef, ex);
        }
    }

    private String detectMimeType(String storageRef) {
        String lower = storageRef.toLowerCase();
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "image/png";
    }

    private String firstNonBlank(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback;
    }
}
