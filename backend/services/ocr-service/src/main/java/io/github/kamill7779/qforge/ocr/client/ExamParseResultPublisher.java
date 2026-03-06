package io.github.kamill7779.qforge.ocr.client;

import io.github.kamill7779.qforge.common.contract.ExamParseCompletedEvent;
import io.github.kamill7779.qforge.common.contract.ExamParseQuestionResultEvent;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * 试卷解析结果 MQ 发布器。
 */
@Component
public class ExamParseResultPublisher {

    private static final Logger log = LoggerFactory.getLogger(ExamParseResultPublisher.class);

    private final RabbitTemplate rabbitTemplate;

    public ExamParseResultPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * 发布单题解析结果。
     */
    public void publishQuestionResult(
            String taskUuid,
            int seqNo,
            String questionType,
            String rawStemText,
            String stemXml,
            String rawAnswerText,
            String answerXml,
            List<ExtractedImage> stemImages,
            List<ExtractedImage> answerImages,
            List<Integer> sourcePages,
            boolean parseError,
            String errorMsg) {

        ExamParseQuestionResultEvent event = new ExamParseQuestionResultEvent(
                taskUuid, seqNo, questionType,
                rawStemText, stemXml,
                rawAnswerText, answerXml,
                stemImages, answerImages, sourcePages,
                parseError, errorMsg
        );

        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.EXAM_EXCHANGE,
                RabbitTopologyConfig.ROUTING_EXAM_PARSE_RESULT,
                event);

        log.info("Published exam parse question result: taskUuid={}, seq={}", taskUuid, seqNo);
    }

    /**
     * 发布整卷解析完成事件。
     */
    public void publishCompleted(String taskUuid, String status,
                                  int questionCount, String errorMsg) {
        ExamParseCompletedEvent event = new ExamParseCompletedEvent(
                taskUuid, status, questionCount, errorMsg);

        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.EXAM_EXCHANGE,
                RabbitTopologyConfig.ROUTING_EXAM_PARSE_COMPLETED,
                event);

        log.info("Published exam parse completed: taskUuid={}, status={}, questionCount={}",
                taskUuid, status, questionCount);
    }
}
