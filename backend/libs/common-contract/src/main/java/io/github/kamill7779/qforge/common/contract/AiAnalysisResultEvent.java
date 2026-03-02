package io.github.kamill7779.qforge.common.contract;

import java.math.BigDecimal;
import java.util.List;

public record AiAnalysisResultEvent(
        String questionUuid,
        String userId,
        boolean success,
        List<String> suggestedTags,
        BigDecimal suggestedDifficulty,
        String reasoning,
        String errorMessage
) {
}
