package io.github.kamill7779.qforge.common.contract;

import java.time.Instant;
import java.util.UUID;

public record ProblemSummaryEvent(
        UUID problemId,
        UUID userId,
        String title,
        Instant updatedAt
) {
}
