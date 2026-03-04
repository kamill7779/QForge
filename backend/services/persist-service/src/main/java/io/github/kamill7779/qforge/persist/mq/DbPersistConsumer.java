package io.github.kamill7779.qforge.persist.mq;

import io.github.kamill7779.qforge.common.contract.DbPersistConstants;
import io.github.kamill7779.qforge.common.contract.DbWriteBackEvent;
import io.github.kamill7779.qforge.persist.entity.AiAnalysisTask;
import io.github.kamill7779.qforge.persist.entity.OcrTask;
import io.github.kamill7779.qforge.persist.entity.QuestionAiTask;
import io.github.kamill7779.qforge.persist.entity.QuestionOcrTask;
import io.github.kamill7779.qforge.persist.repository.AiAnalysisTaskRepository;
import io.github.kamill7779.qforge.persist.repository.OcrTaskRepository;
import io.github.kamill7779.qforge.persist.repository.QuestionAiTaskRepository;
import io.github.kamill7779.qforge.persist.repository.QuestionOcrTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 专职落库消费者 —— 从 {@value DbPersistConstants#DB_PERSIST_QUEUE} 队列消费
 * {@link DbWriteBackEvent}，将 AI/OCR 任务最终状态写入 MySQL。
 *
 * <h3>职责边界</h3>
 * 本消费者 <b>只做 DB 写入</b>，不碰 Redis、不推 WebSocket。
 *
 * <h3>支持的 taskType</h3>
 * <ul>
 *   <li>{@code AI}        — question-service AI 结果 → q_question_ai_task</li>
 *   <li>{@code OCR}       — question-service OCR 结果 → q_question_ocr_task</li>
 *   <li>{@code OCR_LOCAL} — ocr-service 本地 OCR 任务状态 → q_ocr_task</li>
 *   <li>{@code AI_LOCAL}  — ocr-service 本地 AI 分析任务状态 → q_ai_analysis_task</li>
 * </ul>
 *
 * <h3>幂等设计</h3>
 * 若 DB 中对应行已处于终态（APPLIED / CONFIRMED / FAILED / SUCCESS），跳过本次写入。
 */
@Component
public class DbPersistConsumer {

    private static final Logger log = LoggerFactory.getLogger(DbPersistConsumer.class);

    private final QuestionAiTaskRepository questionAiTaskRepository;
    private final QuestionOcrTaskRepository questionOcrTaskRepository;
    private final OcrTaskRepository ocrTaskRepository;
    private final AiAnalysisTaskRepository aiAnalysisTaskRepository;

    public DbPersistConsumer(
            QuestionAiTaskRepository questionAiTaskRepository,
            QuestionOcrTaskRepository questionOcrTaskRepository,
            OcrTaskRepository ocrTaskRepository,
            AiAnalysisTaskRepository aiAnalysisTaskRepository
    ) {
        this.questionAiTaskRepository = questionAiTaskRepository;
        this.questionOcrTaskRepository = questionOcrTaskRepository;
        this.ocrTaskRepository = ocrTaskRepository;
        this.aiAnalysisTaskRepository = aiAnalysisTaskRepository;
    }

    @RabbitListener(queues = DbPersistConstants.DB_PERSIST_QUEUE)
    @Transactional
    public void onDbPersist(DbWriteBackEvent event) {
        log.debug("DbPersistConsumer received taskType={} taskUuid={} status={}",
                event.taskType(), event.taskUuid(), event.status());
        switch (event.taskType()) {
            case "AI"        -> persistQuestionAiTask(event);
            case "OCR"       -> persistQuestionOcrTask(event);
            case "OCR_LOCAL" -> persistOcrTask(event);
            case "AI_LOCAL"  -> persistAiAnalysisTask(event);
            default -> log.warn("Unknown taskType={} for taskUuid={}, skipping",
                    event.taskType(), event.taskUuid());
        }
    }

    // ======================= question-service: AI =======================

    private void persistQuestionAiTask(DbWriteBackEvent event) {
        QuestionAiTask task = questionAiTaskRepository.findByTaskUuid(event.taskUuid()).orElse(null);

        if (task == null) {
            log.warn("q_question_ai_task row not found for taskUuid={}, creating upsert row", event.taskUuid());
            task = new QuestionAiTask();
            task.setTaskUuid(event.taskUuid());
            task.setQuestionUuid(event.questionUuid());
            task.setRequestUser(event.userId());
            task.setStatus(event.status());
            task.setSuggestedTags(event.suggestedTagsJson());
            task.setSuggestedDifficulty(event.suggestedDifficulty());
            task.setReasoning(event.reasoning());
            task.setErrorMsg(event.errorMsg());
            questionAiTaskRepository.insert(task);
            log.info("Upserted q_question_ai_task taskUuid={} status={}", event.taskUuid(), event.status());
            return;
        }

        if ("APPLIED".equals(task.getStatus())) {
            log.info("q_question_ai_task already APPLIED, skip taskUuid={}", event.taskUuid());
            return;
        }
        if (event.status().equals(task.getStatus())) {
            log.debug("q_question_ai_task already status={}, skip duplicate taskUuid={}",
                    task.getStatus(), event.taskUuid());
            return;
        }

        task.setStatus(event.status());
        if ("SUCCESS".equals(event.status())) {
            task.setSuggestedTags(event.suggestedTagsJson());
            task.setSuggestedDifficulty(event.suggestedDifficulty());
            task.setReasoning(event.reasoning());
        } else {
            task.setErrorMsg(event.errorMsg());
        }
        questionAiTaskRepository.updateById(task);
        log.info("Persisted q_question_ai_task taskUuid={} status={}", event.taskUuid(), event.status());
    }

    // ======================= question-service: OCR =======================

    private void persistQuestionOcrTask(DbWriteBackEvent event) {
        QuestionOcrTask task = questionOcrTaskRepository.findByTaskUuid(event.taskUuid()).orElse(null);

        if (task == null) {
            if (event.userId() == null) {
                log.warn("q_question_ocr_task row not found and userId is null for taskUuid={}, will retry",
                        event.taskUuid());
                throw new IllegalStateException(
                        "q_question_ocr_task row not ready and userId unknown for taskUuid=" + event.taskUuid());
            }
            log.warn("q_question_ocr_task row not found for taskUuid={}, creating upsert row", event.taskUuid());
            task = new QuestionOcrTask();
            task.setTaskUuid(event.taskUuid());
            task.setQuestionUuid(event.questionUuid());
            task.setBizType(event.bizType());
            task.setRequestUser(event.userId());
            task.setStatus(event.status());
            task.setRecognizedText(event.recognizedText());
            task.setErrorMsg(event.errorMsg());
            questionOcrTaskRepository.save(task);
            log.info("Upserted q_question_ocr_task taskUuid={} status={}", event.taskUuid(), event.status());
            return;
        }

        if ("CONFIRMED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            log.info("q_question_ocr_task already terminal status={}, skip taskUuid={}",
                    task.getStatus(), event.taskUuid());
            return;
        }

        task.setStatus(event.status());
        task.setRecognizedText(event.recognizedText());
        task.setErrorMsg(event.errorMsg());
        questionOcrTaskRepository.save(task);
        log.info("Persisted q_question_ocr_task taskUuid={} status={}", event.taskUuid(), event.status());
    }

    // ======================= ocr-service: OCR_LOCAL =======================

    private void persistOcrTask(DbWriteBackEvent event) {
        OcrTask task = ocrTaskRepository.findByTaskUuid(event.taskUuid()).orElse(null);

        if (task == null) {
            // 初始行由 ocr-service createTask() REST 接口创建，此处极端竞态保底
            log.warn("q_ocr_task row not found for taskUuid={}, will retry", event.taskUuid());
            throw new IllegalStateException(
                    "q_ocr_task row not ready for taskUuid=" + event.taskUuid());
        }

        // 幂等：终态不允许回退
        if ("SUCCESS".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            log.info("q_ocr_task already terminal status={}, skip taskUuid={}",
                    task.getStatus(), event.taskUuid());
            return;
        }

        task.setStatus(event.status());
        if ("SUCCESS".equals(event.status())) {
            task.setRecognizedText(event.recognizedText());
            task.setErrorMsg(null);
        } else if ("FAILED".equals(event.status())) {
            task.setErrorMsg(event.errorMsg());
        }
        // PROCESSING 只更新 status
        ocrTaskRepository.save(task);
        log.info("Persisted q_ocr_task taskUuid={} status={}", event.taskUuid(), event.status());
    }

    // ======================= ocr-service: AI_LOCAL =======================

    private void persistAiAnalysisTask(DbWriteBackEvent event) {
        AiAnalysisTask task = aiAnalysisTaskRepository.findByTaskUuid(event.taskUuid()).orElse(null);

        if ("PROCESSING".equals(event.status())) {
            // 初始化插入（PROCESSING 是消费者开始处理时发出的第一条事件）
            if (task != null) {
                log.debug("q_ai_analysis_task already exists for taskUuid={}, skip PROCESSING insert",
                        event.taskUuid());
                return;
            }
            task = new AiAnalysisTask();
            task.setTaskUuid(event.taskUuid());
            task.setQuestionUuid(event.questionUuid());
            task.setStatus("PROCESSING");
            task.setModel(event.model());
            task.setUserPrompt(event.userPrompt());
            task.setRequestUser(event.userId());
            aiAnalysisTaskRepository.insert(task);
            log.info("Inserted q_ai_analysis_task taskUuid={} status=PROCESSING", event.taskUuid());
            return;
        }

        // SUCCESS / FAILED 更新
        if (task == null) {
            // PROCESSING 事件尚未被消费（极端竞态），直接创建完整行
            log.warn("q_ai_analysis_task row not found for taskUuid={} status={}, creating full row",
                    event.taskUuid(), event.status());
            task = new AiAnalysisTask();
            task.setTaskUuid(event.taskUuid());
            task.setQuestionUuid(event.questionUuid());
            task.setStatus(event.status());
            task.setModel(event.model());
            task.setUserPrompt(event.userPrompt());
            task.setRequestUser(event.userId());
        } else {
            // 幂等：终态不允许回退
            if ("SUCCESS".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
                log.info("q_ai_analysis_task already terminal status={}, skip taskUuid={}",
                        task.getStatus(), event.taskUuid());
                return;
            }
            task.setStatus(event.status());
        }

        // userPrompt 在 PROCESSING 阶段可能为 null（尚未构建），SUCCESS/FAILED 时补齐
        if (event.userPrompt() != null) {
            task.setUserPrompt(event.userPrompt());
        }
        if ("SUCCESS".equals(event.status())) {
            task.setRawResponse(event.rawResponse());
            task.setSuggestedTags(event.suggestedTagsJson());
            task.setSuggestedDifficulty(event.suggestedDifficulty());
            task.setReasoning(event.reasoning());
        } else {
            task.setErrorMsg(event.errorMsg());
            task.setRawResponse(event.rawResponse());
        }

        if (task.getId() == null) {
            aiAnalysisTaskRepository.insert(task);
        } else {
            aiAnalysisTaskRepository.updateById(task);
        }
        log.info("Persisted q_ai_analysis_task taskUuid={} status={}", event.taskUuid(), event.status());
    }
}
