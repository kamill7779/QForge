package io.github.kamill7779.qforge.common.contract;

import java.util.List;

public record AiAnalysisTaskCreatedEvent(
        String taskUuid,
        String questionUuid,
        String userId,
        String stemXml,
        List<String> answerTexts
) {
}
