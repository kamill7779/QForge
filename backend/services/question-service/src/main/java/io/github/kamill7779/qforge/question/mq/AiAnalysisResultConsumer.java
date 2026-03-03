package io.github.kamill7779.qforge.question.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.AiAnalysisResultEvent;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.entity.QuestionAiTask;
import io.github.kamill7779.qforge.question.repository.QuestionAiTaskRepository;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisResultConsumer.class);

    private final OcrWsPushService wsPushService;
    private final QuestionAiTaskRepository questionAiTaskRepository;
    private final ObjectMapper objectMapper;

    public AiAnalysisResultConsumer(OcrWsPushService wsPushService,
                                    QuestionAiTaskRepository questionAiTaskRepository,
                                    ObjectMapper objectMapper) {
        this.wsPushService = wsPushService;
        this.questionAiTaskRepository = questionAiTaskRepository;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitTopologyConfig.AI_ANALYSIS_RESULT_QUEUE)
    public void onAiAnalysisResult(AiAnalysisResultEvent event) {
        log.info("Received AI analysis result for question={}, taskUuid={}, success={}",
                event.questionUuid(), event.taskUuid(), event.success());

        // ---- Persist result to q_question_ai_task ----
        if (event.taskUuid() != null) {
            questionAiTaskRepository.findByTaskUuid(event.taskUuid()).ifPresent(task -> {
                if (event.success()) {
                    task.setStatus("SUCCESS");
                    try {
                        task.setSuggestedTags(objectMapper.writeValueAsString(
                                event.suggestedTags() != null ? event.suggestedTags() : List.of()));
                    } catch (Exception ignored) {}
                    task.setSuggestedDifficulty(event.suggestedDifficulty());
                    task.setReasoning(event.reasoning());
                } else {
                    task.setStatus("FAILED");
                    task.setErrorMsg(event.errorMessage());
                }
                questionAiTaskRepository.updateById(task);
                log.info("Updated q_question_ai_task taskUuid={} status={}", task.getTaskUuid(), task.getStatus());
            });
        }

        // ---- WebSocket push (unchanged) ----
        if (event.success()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("questionUuid", event.questionUuid());
            payload.put("taskUuid", event.taskUuid());
            payload.put("suggestedTags", event.suggestedTags());
            payload.put("suggestedDifficulty", event.suggestedDifficulty());
            payload.put("reasoning", event.reasoning());
            wsPushService.push(event.userId(), "ai.analysis.succeeded", payload);
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("questionUuid", event.questionUuid());
            payload.put("taskUuid", event.taskUuid());
            payload.put("errorMessage", event.errorMessage() == null ? "Unknown error" : event.errorMessage());
            wsPushService.push(event.userId(), "ai.analysis.failed", payload);
        }
    }
}
