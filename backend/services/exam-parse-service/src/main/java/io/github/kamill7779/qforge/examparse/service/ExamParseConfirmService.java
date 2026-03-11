package io.github.kamill7779.qforge.examparse.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.examparse.entity.ExamParseQuestion;
import io.github.kamill7779.qforge.examparse.entity.ExamParseTask;
import io.github.kamill7779.qforge.examparse.repository.ExamParseQuestionRepository;
import io.github.kamill7779.qforge.examparse.repository.ExamParseTaskRepository;
import io.github.kamill7779.qforge.internal.api.CreateQuestionFromParseRequest;
import io.github.kamill7779.qforge.internal.api.CreateQuestionFromParseResponse;
import io.github.kamill7779.qforge.internal.api.QuestionCoreClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 试卷解析确认服务 —— 将暂存题目通过 Feign 调用 question-core-service 创建为正式题目。
 * <p>
 * 【微服务拆分重构】原先直接写入 q_question / q_answer / q_question_asset / q_answer_asset，
 * 现改为调用 QuestionCoreClient.createFromParse() Feign 接口，由 question-core-service 负责创建。
 */
@Service
public class ExamParseConfirmService {

    private static final Logger log = LoggerFactory.getLogger(ExamParseConfirmService.class);

    private final ExamParseTaskRepository taskRepository;
    private final ExamParseQuestionRepository examQuestionRepository;
    private final QuestionCoreClient questionCoreClient;
    private final ObjectMapper objectMapper;

    public ExamParseConfirmService(
            ExamParseTaskRepository taskRepository,
            ExamParseQuestionRepository examQuestionRepository,
            QuestionCoreClient questionCoreClient,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.examQuestionRepository = examQuestionRepository;
        this.questionCoreClient = questionCoreClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 批量确认：为所有 PENDING 题目创建正式记录。
     *
     * @return 确认的题目数
     */
    @Transactional
    public int confirm(String taskUuid, String ownerUser) {
        requireActiveTask(taskUuid, ownerUser);

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

    /**
     * 通过 Feign 调用 question-core-service 创建正式题目。
     */
    private String confirmSingleQuestion(ExamParseQuestion epq, String ownerUser) {
        // 解析图片 JSON → Map<refKey, base64>
        Map<String, String> stemImageMap = parseImageMap(epq.getStemImagesJson());
        Map<String, String> answerImageMap = parseImageMap(epq.getAnswerImagesJson());

        CreateQuestionFromParseRequest request = new CreateQuestionFromParseRequest(
                ownerUser,
                epq.getStemXml(),
                epq.getAnswerXml(),
                epq.getDifficulty(),
                stemImageMap,
                answerImageMap,
                epq.getMainTagsJson(),
                epq.getSecondaryTagsJson()
        );

        CreateQuestionFromParseResponse response = questionCoreClient.createFromParse(request);
        return response.questionUuid();
    }

    /**
     * 单题确认入库。
     */
    @Transactional
    public String confirmSingle(String taskUuid, int seqNo, String ownerUser) {
        requireActiveTask(taskUuid, ownerUser);

        ExamParseQuestion epq = examQuestionRepository.findByTaskUuidAndSeqNo(taskUuid, seqNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "题目不存在: taskUuid=" + taskUuid + ", seqNo=" + seqNo));

        if (!"PENDING".equals(epq.getConfirmStatus())) {
            throw new IllegalStateException("题目状态不是 PENDING，无法确认: " + epq.getConfirmStatus());
        }

        String questionUuid = confirmSingleQuestion(epq, ownerUser);
        epq.setQuestionUuid(questionUuid);
        epq.setConfirmStatus("CONFIRMED");
        examQuestionRepository.save(epq);

        log.info("Single question confirmed: taskUuid={}, seqNo={}, questionUuid={}",
                taskUuid, seqNo, questionUuid);
        return questionUuid;
    }

    /**
     * 单题跳过。
     */
    @Transactional
    public void skipQuestion(String taskUuid, int seqNo, String ownerUser) {
        requireActiveTask(taskUuid, ownerUser);

        ExamParseQuestion epq = examQuestionRepository.findByTaskUuidAndSeqNo(taskUuid, seqNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "题目不存在: taskUuid=" + taskUuid + ", seqNo=" + seqNo));

        if (!"PENDING".equals(epq.getConfirmStatus())) {
            throw new IllegalStateException("题目状态不是 PENDING，无法跳过: " + epq.getConfirmStatus());
        }

        epq.setConfirmStatus("SKIPPED");
        examQuestionRepository.save(epq);
        log.info("Question skipped: taskUuid={}, seqNo={}", taskUuid, seqNo);
    }

    /**
     * 恢复已跳过的题目。
     */
    @Transactional
    public void unskipQuestion(String taskUuid, int seqNo, String ownerUser) {
        requireActiveTask(taskUuid, ownerUser);

        ExamParseQuestion epq = examQuestionRepository.findByTaskUuidAndSeqNo(taskUuid, seqNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "题目不存在: taskUuid=" + taskUuid + ", seqNo=" + seqNo));

        if (!"SKIPPED".equals(epq.getConfirmStatus())) {
            throw new IllegalStateException("题目状态不是 SKIPPED，无法恢复: " + epq.getConfirmStatus());
        }

        epq.setConfirmStatus("PENDING");
        examQuestionRepository.save(epq);
        log.info("Question unskipped: taskUuid={}, seqNo={}", taskUuid, seqNo);
    }

    /**
     * 将 ExtractedImage JSON 解析为 refKey → base64 的 Map。
     */
    private Map<String, String> parseImageMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            List<ExtractedImage> images = objectMapper.readValue(json, new TypeReference<>() {});
            Map<String, String> result = new HashMap<>();
            for (ExtractedImage img : images) {
                if (img.refKey() != null && img.imageBase64() != null) {
                    result.put(img.refKey(), img.imageBase64());
                }
            }
            return result;
        } catch (Exception ex) {
            log.warn("Failed to parse images JSON: {}", ex.getMessage());
            return Map.of();
        }
    }

    private ExamParseTask requireActiveTask(String taskUuid, String ownerUser) {
        ExamParseTask task = taskRepository.findByTaskUuidAndOwnerUser(taskUuid, ownerUser)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在或无权访问: " + taskUuid));
        if ("CANCELLED".equals(task.getStatus())) {
            throw new IllegalStateException("任务已取消，无法继续操作: " + taskUuid);
        }
        return task;
    }
}
