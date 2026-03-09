package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.internal.api.CreateQuestionFromParseRequest;
import io.github.kamill7779.qforge.internal.api.CreateQuestionFromParseResponse;
import io.github.kamill7779.qforge.question.entity.Answer;
import io.github.kamill7779.qforge.question.entity.AnswerAsset;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionAsset;
import io.github.kamill7779.qforge.question.repository.AnswerAssetRepository;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ParsedQuestionCreateService {

    private static final Logger log = LoggerFactory.getLogger(ParsedQuestionCreateService.class);

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionAssetRepository questionAssetRepository;
    private final AnswerAssetRepository answerAssetRepository;
    private final QuestionTagAssignmentService questionTagAssignmentService;
    private final QuestionSummaryQueryService questionSummaryQueryService;

    public ParsedQuestionCreateService(
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
    public CreateQuestionFromParseResponse create(CreateQuestionFromParseRequest request) {
        String questionUuid = UUID.randomUUID().toString();
        boolean hasAnswer = request.answerXml() != null && !request.answerXml().isBlank();

        Question question = new Question();
        question.setQuestionUuid(questionUuid);
        question.setOwnerUser(request.ownerUser());
        question.setStemText(request.stemXml());
        question.setStatus(hasAnswer ? "READY" : "DRAFT");
        question.setVisibility("PRIVATE");
        question.setDeleted(false);
        question.setSource("未分类");
        if (request.difficulty() != null) {
            question.setDifficulty(request.difficulty());
        }
        questionRepository.save(question);

        saveStemAssets(question, request.stemImages());
        if (hasAnswer) {
            saveOfficialAnswer(question, request.answerXml(), request.answerImages());
        }

        questionTagAssignmentService.applyFromParsePayload(
                question,
                request.mainTagsJson(),
                request.secondaryTagsJson(),
                request.ownerUser()
        );
        questionSummaryQueryService.evict(request.ownerUser(), questionUuid);

        log.info("Question created from parse: questionUuid={}, hasAnswer={}", questionUuid, hasAnswer);
        return new CreateQuestionFromParseResponse(questionUuid, question.getId());
    }

    private void saveStemAssets(Question question, Map<String, String> stemImages) {
        if (stemImages == null || stemImages.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : stemImages.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            QuestionAsset asset = new QuestionAsset();
            asset.setAssetUuid(UUID.randomUUID().toString());
            asset.setQuestionId(question.getId());
            asset.setAssetType("INLINE_IMAGE");
            asset.setRefKey(entry.getKey());
            asset.setImageData(entry.getValue());
            asset.setMimeType("image/png");
            questionAssetRepository.save(asset);
        }
    }

    private void saveOfficialAnswer(Question question, String answerXml, Map<String, String> answerImages) {
        Answer answer = new Answer();
        answer.setAnswerUuid(UUID.randomUUID().toString());
        answer.setQuestionId(question.getId());
        answer.setLatexText(answerXml);
        answer.setAnswerType("SOLUTION");
        answer.setSortOrder(0);
        answer.setOfficial(true);
        answer.setDeleted(false);
        answerRepository.save(answer);

        if (answerImages == null || answerImages.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : answerImages.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            AnswerAsset asset = new AnswerAsset();
            asset.setAssetUuid(UUID.randomUUID().toString());
            asset.setQuestionId(question.getId());
            asset.setAnswerId(answer.getId());
            asset.setRefKey(entry.getKey());
            asset.setImageData(entry.getValue());
            asset.setMimeType("image/png");
            answerAssetRepository.save(asset);
        }
    }
}
