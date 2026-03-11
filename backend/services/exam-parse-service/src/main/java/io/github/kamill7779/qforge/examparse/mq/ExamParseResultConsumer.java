package io.github.kamill7779.qforge.examparse.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.ExamParseCompletedEvent;
import io.github.kamill7779.qforge.common.contract.ExamParseQuestionResultEvent;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.examparse.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.examparse.entity.ExamParseQuestion;
import io.github.kamill7779.qforge.examparse.entity.ExamParseTask;
import io.github.kamill7779.qforge.examparse.repository.ExamParseQuestionRepository;
import io.github.kamill7779.qforge.examparse.repository.ExamParseTaskRepository;
import io.github.kamill7779.qforge.examparse.ws.WsPushService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 试卷解析 MQ 消费者 —— 监听 ocr-service 发布的逐题结果 + 整卷完成事件，
 * 写入 q_exam_parse_question 并通过 Redis Pub/Sub → question-core WS 推送进度。
 */
@Component
public class ExamParseResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExamParseResultConsumer.class);

    private final ExamParseTaskRepository taskRepository;
    private final ExamParseQuestionRepository questionRepository;
    private final WsPushService wsPushService;
    private final ObjectMapper objectMapper;

    public ExamParseResultConsumer(ExamParseTaskRepository taskRepository,
                                    ExamParseQuestionRepository questionRepository,
                                    WsPushService wsPushService,
                                    ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.questionRepository = questionRepository;
        this.wsPushService = wsPushService;
        this.objectMapper = objectMapper;
    }

    // ===================== 逐题结果 =====================

    @RabbitListener(queues = RabbitTopologyConfig.EXAM_PARSE_RESULT_QUEUE)
    public void onQuestionResult(ExamParseQuestionResultEvent event) {
        log.info("Received exam parse question result: taskUuid={}, seq={}",
                event.taskUuid(), event.seqNo());
        try {
            Optional<ExamParseTask> taskOpt = taskRepository.findByTaskUuid(event.taskUuid());
            if (taskOpt.isEmpty() || "CANCELLED".equals(taskOpt.get().getStatus())) {
                log.info("Drop late exam parse question result for taskUuid={}, seq={}", event.taskUuid(), event.seqNo());
                return;
            }
            ExamParseQuestion q = questionRepository.findByTaskUuidAndSeqNo(event.taskUuid(), event.seqNo())
                    .orElseGet(ExamParseQuestion::new);
            q.setTaskUuid(event.taskUuid());
            q.setSeqNo(event.seqNo());
            q.setQuestionType(event.questionType());
            q.setRawStemText(event.rawStemText());
            q.setStemXml(event.stemXml());
            q.setRawAnswerText(event.rawAnswerText());
            q.setAnswerXml(event.answerXml());
            q.setStemImagesJson(serializeImages(event.stemImages()));
            q.setAnswerImagesJson(serializeImages(event.answerImages()));
            q.setSourcePages(serializePages(event.sourcePages()));
            q.setParseError(event.parseError());
            q.setErrorMsg(event.errorMsg());
            q.setConfirmStatus("PENDING");
            questionRepository.save(q);

            resolveOwnerUser(event.taskUuid()).ifPresent(ownerUser -> {
                Map<String, Object> payload = new HashMap<>();
                payload.put("taskUuid", event.taskUuid());
                payload.put("seqNo", event.seqNo());
                payload.put("questionType", event.questionType());
                payload.put("parseError", event.parseError());
                payload.put("errorMsg", event.errorMsg());
                wsPushService.push(ownerUser, "exam.parse.question.result", payload);
            });

            log.info("Saved exam parse question: taskUuid={}, seq={}", event.taskUuid(), event.seqNo());
        } catch (Exception ex) {
            log.error("Failed to process exam parse question result: taskUuid={}, seq={}, error={}",
                    event.taskUuid(), event.seqNo(), ex.getMessage(), ex);
        }
    }

    // ===================== 整卷完成 =====================

    @RabbitListener(queues = RabbitTopologyConfig.EXAM_PARSE_COMPLETED_QUEUE)
    public void onCompleted(ExamParseCompletedEvent event) {
        log.info("Received exam parse completed: taskUuid={}, status={}, questionCount={}",
                event.taskUuid(), event.status(), event.questionCount());
        try {
            taskRepository.findByTaskUuid(event.taskUuid()).ifPresent(task -> {
                if ("CANCELLED".equals(task.getStatus())) {
                    log.info("Drop late exam parse completed event for cancelled taskUuid={}", event.taskUuid());
                    return;
                }
                task.setStatus(event.status());
                task.setProgress(100);
                task.setQuestionCount(event.questionCount());
                if (event.errorMsg() != null) {
                    task.setErrorMsg(truncate(event.errorMsg(), 2000));
                }
                taskRepository.save(task);
            });

            resolveOwnerUser(event.taskUuid()).ifPresent(ownerUser -> {
                Map<String, Object> payload = new HashMap<>();
                payload.put("taskUuid", event.taskUuid());
                payload.put("status", event.status());
                payload.put("questionCount", event.questionCount());
                payload.put("errorMsg", event.errorMsg());
                wsPushService.push(ownerUser, "exam.parse.completed", payload);
            });

            log.info("Exam parse completed event processed: taskUuid={}", event.taskUuid());
        } catch (Exception ex) {
            log.error("Failed to process exam parse completed event: taskUuid={}, error={}",
                    event.taskUuid(), ex.getMessage(), ex);
        }
    }

    // ===================== 辅助方法 =====================

    private Optional<String> resolveOwnerUser(String taskUuid) {
        return taskRepository.findByTaskUuid(taskUuid)
                .map(ExamParseTask::getOwnerUser);
    }

    private String serializeImages(List<ExtractedImage> images) {
        if (images == null || images.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(images);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize images: {}", ex.getMessage());
            return null;
        }
    }

    private String serializePages(List<Integer> pages) {
        if (pages == null || pages.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(pages);
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize pages: {}", ex.getMessage());
            return null;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
