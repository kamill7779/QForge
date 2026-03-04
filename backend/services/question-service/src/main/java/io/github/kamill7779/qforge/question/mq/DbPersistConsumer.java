package io.github.kamill7779.qforge.question.mq;

import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.dto.DbWriteBackEvent;
import io.github.kamill7779.qforge.question.entity.QuestionAiTask;
import io.github.kamill7779.qforge.question.entity.QuestionOcrTask;
import io.github.kamill7779.qforge.question.repository.QuestionAiTaskRepository;
import io.github.kamill7779.qforge.question.repository.QuestionOcrTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 专职落库消费者：从 {@value RabbitTopologyConfig#DB_PERSIST_QUEUE} 队列
 * 消费 {@link DbWriteBackEvent}，将 AI / OCR 任务最终状态写入 MySQL。
 *
 * <h3>职责边界</h3>
 * <ul>
 *   <li>本消费者 <b>只做 DB 写入</b>，不碰 Redis、不推 WebSocket。</li>
 *   <li>业务消费者（AI/OCR Result Consumer）负责 Redis 热更新 + WS 推送，
 *       写完立即投递本事件后返回。</li>
 * </ul>
 *
 * <h3>幂等设计</h3>
 * <ul>
 *   <li>若 DB 中对应行已处于终态（APPLIED / CONFIRMED / FAILED），跳过本次写入。</li>
 *   <li>若 DB 中尚无对应行（极端竞态：写回消息比 INSERT 早到），执行 upsert 保底。</li>
 * </ul>
 *
 * <h3>重试 / 死信</h3>
 * 异常直接上抛，由 {@code application.yml} 中已配置的 RabbitMQ 监听器重试策略
 * （3 次 × 指数退避）接管；超限后进死信队列（如有配置）。
 */
@Component
public class DbPersistConsumer {

    private static final Logger log = LoggerFactory.getLogger(DbPersistConsumer.class);

    private final QuestionAiTaskRepository questionAiTaskRepository;
    private final QuestionOcrTaskRepository questionOcrTaskRepository;

    public DbPersistConsumer(
            QuestionAiTaskRepository questionAiTaskRepository,
            QuestionOcrTaskRepository questionOcrTaskRepository
    ) {
        this.questionAiTaskRepository = questionAiTaskRepository;
        this.questionOcrTaskRepository = questionOcrTaskRepository;
    }

    @RabbitListener(queues = RabbitTopologyConfig.DB_PERSIST_QUEUE)
    @Transactional
    public void onDbPersist(DbWriteBackEvent event) {
        log.debug("DbPersistConsumer received taskType={} taskUuid={} status={}",
                event.taskType(), event.taskUuid(), event.status());
        if ("AI".equals(event.taskType())) {
            persistAiTask(event);
        } else if ("OCR".equals(event.taskType())) {
            persistOcrTask(event);
        } else {
            log.warn("Unknown taskType={} for taskUuid={}, skipping", event.taskType(), event.taskUuid());
        }
    }

    // =============================== AI ================================

    private void persistAiTask(DbWriteBackEvent event) {
        QuestionAiTask task = questionAiTaskRepository.findByTaskUuid(event.taskUuid()).orElse(null);

        if (task == null) {
            // 极端竞态：写回消息比 requestAiAnalysis 的事务提交早到，做保底 insert
            log.warn("AI task DB row not found for taskUuid={}, creating upsert row", event.taskUuid());
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
            log.info("Upserted AI task row taskUuid={} status={}", event.taskUuid(), event.status());
            return;
        }

        // 幂等：终态 APPLIED 不允许被写回覆盖（用户已应用该推荐）
        if ("APPLIED".equals(task.getStatus())) {
            log.info("AI task already APPLIED, skipping write-back for taskUuid={}", event.taskUuid());
            return;
        }
        // 幂等：同状态重复消费
        if (event.status().equals(task.getStatus())) {
            log.debug("AI task already in status={}, skip duplicate write-back for taskUuid={}",
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
        log.info("Persisted AI task taskUuid={} status={}", event.taskUuid(), event.status());
    }

    // =============================== OCR ================================

    private void persistOcrTask(DbWriteBackEvent event) {
        QuestionOcrTask task = questionOcrTaskRepository.findByTaskUuid(event.taskUuid()).orElse(null);

        if (task == null) {
            // 极端竞态：DB 行尚未可见，做保底 insert
            if (event.userId() == null) {
                // 无法构建完整行（Redis 已过期），跳过；下次重试若 DB 行已写入则正常更新
                log.warn("OCR task DB row not found and userId is null for taskUuid={}, will retry", event.taskUuid());
                throw new IllegalStateException(
                        "OCR task row not ready and userId unknown for taskUuid=" + event.taskUuid());
            }
            log.warn("OCR task DB row not found for taskUuid={}, creating upsert row", event.taskUuid());
            task = new QuestionOcrTask();
            task.setTaskUuid(event.taskUuid());
            task.setQuestionUuid(event.questionUuid());
            task.setBizType(event.bizType());
            task.setRequestUser(event.userId());
            task.setStatus(event.status());
            task.setRecognizedText(event.recognizedText());
            task.setErrorMsg(event.errorMsg());
            questionOcrTaskRepository.save(task);
            log.info("Upserted OCR task row taskUuid={} status={}", event.taskUuid(), event.status());
            return;
        }

        // 幂等：已处于终态不允许回退
        if ("CONFIRMED".equals(task.getStatus()) || "FAILED".equals(task.getStatus())) {
            log.info("OCR task already in terminal status={}, skipping write-back for taskUuid={}",
                    task.getStatus(), event.taskUuid());
            return;
        }

        task.setStatus(event.status());
        task.setRecognizedText(event.recognizedText());
        task.setErrorMsg(event.errorMsg());
        questionOcrTaskRepository.save(task);
        log.info("Persisted OCR task taskUuid={} status={}", event.taskUuid(), event.status());
    }
}
