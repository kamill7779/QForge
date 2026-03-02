package io.github.kamill7779.qforge.question.mq;

import io.github.kamill7779.qforge.common.contract.AiAnalysisResultEvent;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class AiAnalysisResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisResultConsumer.class);

    private final OcrWsPushService wsPushService;

    public AiAnalysisResultConsumer(OcrWsPushService wsPushService) {
        this.wsPushService = wsPushService;
    }

    @RabbitListener(queues = RabbitTopologyConfig.AI_ANALYSIS_RESULT_QUEUE)
    public void onAiAnalysisResult(AiAnalysisResultEvent event) {
        log.info("Received AI analysis result for question={}, success={}", event.questionUuid(), event.success());

        if (event.success()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("questionUuid", event.questionUuid());
            payload.put("suggestedTags", event.suggestedTags());
            payload.put("suggestedDifficulty", event.suggestedDifficulty());
            payload.put("reasoning", event.reasoning());
            wsPushService.push(event.userId(), "ai.analysis.succeeded", payload);
        } else {
            Map<String, Object> payload = new HashMap<>();
            payload.put("questionUuid", event.questionUuid());
            payload.put("errorMessage", event.errorMessage() == null ? "Unknown error" : event.errorMessage());
            wsPushService.push(event.userId(), "ai.analysis.failed", payload);
        }
    }
}
