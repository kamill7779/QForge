package io.github.kamill7779.qforge.question.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.question.entity.Answer;
import io.github.kamill7779.qforge.question.entity.AnswerAsset;
import io.github.kamill7779.qforge.question.entity.ExamParseQuestion;
import io.github.kamill7779.qforge.question.entity.ExamParseTask;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionAsset;
import io.github.kamill7779.qforge.question.repository.AnswerAssetRepository;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.ExamParseQuestionRepository;
import io.github.kamill7779.qforge.question.repository.ExamParseTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 试卷解析确认服务 —— 将暂存题目创建为正式 question + answer + 资产。
 */
@Service
public class ExamParseConfirmService {

    private static final Logger log = LoggerFactory.getLogger(ExamParseConfirmService.class);

    private final ExamParseTaskRepository taskRepository;
    private final ExamParseQuestionRepository examQuestionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionAssetRepository questionAssetRepository;
    private final AnswerAssetRepository answerAssetRepository;
    private final ObjectMapper objectMapper;

    public ExamParseConfirmService(
            ExamParseTaskRepository taskRepository,
            ExamParseQuestionRepository examQuestionRepository,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            QuestionAssetRepository questionAssetRepository,
            AnswerAssetRepository answerAssetRepository,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.questionAssetRepository = questionAssetRepository;
        this.answerAssetRepository = answerAssetRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量确认：为所有 PENDING 题目创建正式记录。
     *
     * @return 确认的题目数
     */
    @Transactional
    public int confirm(String taskUuid, String ownerUser) {
        ExamParseTask task = taskRepository.findByTaskUuidAndOwnerUser(taskUuid, ownerUser)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在或无权访问: " + taskUuid));

        List<ExamParseQuestion> pendingQuestions =
                examQuestionRepository.findPendingByTaskUuid(taskUuid);

        if (pendingQuestions.isEmpty()) {
            log.info("No pending questions to confirm for task: {}", taskUuid);
            return 0;
        }

        int confirmed = 0;
        for (ExamParseQuestion epq : pendingQuestions) {
            try {
                String questionUuid = confirmSingleQuestion(epq, ownerUser);
                epq.setQuestionUuid(questionUuid);
                epq.setConfirmStatus("CONFIRMED");
                examQuestionRepository.save(epq);
                confirmed++;
            } catch (Exception ex) {
                log.error("Failed to confirm question seq={} for task={}: {}",
                        epq.getSeqNo(), taskUuid, ex.getMessage(), ex);
                epq.setConfirmStatus("SKIPPED");
                epq.setErrorMsg("Confirm failed: " + ex.getMessage());
                examQuestionRepository.save(epq);
            }
        }

        log.info("Exam parse confirm: taskUuid={}, confirmed={}/{}", taskUuid, confirmed, pendingQuestions.size());
        return confirmed;
    }

    private String confirmSingleQuestion(ExamParseQuestion epq, String ownerUser) {
        String questionUuid = UUID.randomUUID().toString();

        // 创建 q_question
        // 如果有答案 XML，说明题目完整，直接置为 READY；否则 DRAFT 待用户补充答案。
        boolean hasAnswer = epq.getAnswerXml() != null && !epq.getAnswerXml().isBlank();
        Question q = new Question();
        q.setQuestionUuid(questionUuid);
        q.setOwnerUser(ownerUser);
        q.setStemText(epq.getStemXml());
        q.setStatus(hasAnswer ? "READY" : "DRAFT");
        q.setVisibility("PRIVATE");
        q.setDeleted(false);
        questionRepository.save(q);

        // 创建题干图片资产
        List<ExtractedImage> stemImages = parseImages(epq.getStemImagesJson());
        for (ExtractedImage img : stemImages) {
            QuestionAsset asset = new QuestionAsset();
            asset.setQuestionId(q.getId());
            asset.setAssetType("INLINE_IMAGE");
            asset.setRefKey(img.refKey());
            asset.setImageData(img.imageBase64());
            asset.setMimeType(img.mimeType());
            questionAssetRepository.save(asset);
        }

        // 创建 q_answer（如果有答案 XML）
        if (hasAnswer) {
            Answer answer = new Answer();
            answer.setAnswerUuid(UUID.randomUUID().toString());
            answer.setQuestionId(q.getId());
            answer.setLatexText(epq.getAnswerXml());
            answer.setAnswerType("SOLUTION");
            answer.setSortOrder(0);
            answer.setOfficial(true);
            answer.setDeleted(false);
            answerRepository.save(answer);

            // 创建答案图片资产
            List<ExtractedImage> answerImages = parseImages(epq.getAnswerImagesJson());
            for (ExtractedImage img : answerImages) {
                AnswerAsset asset = new AnswerAsset();
                asset.setQuestionId(q.getId());
                asset.setAnswerId(answer.getId());
                asset.setRefKey(img.refKey());
                asset.setImageData(img.imageBase64());
                asset.setMimeType(img.mimeType());
                answerAssetRepository.save(asset);
            }
        }

        return questionUuid;
    }

    @SuppressWarnings("unchecked")
    private List<ExtractedImage> parseImages(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<List<ExtractedImage>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse images JSON: {}", ex.getMessage());
            return List.of();
        }
    }
}
