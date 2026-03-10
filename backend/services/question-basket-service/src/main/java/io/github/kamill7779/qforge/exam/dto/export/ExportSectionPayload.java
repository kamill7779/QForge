package io.github.kamill7779.qforge.exam.dto.export;

import java.util.List;

public record ExportSectionPayload(
        String title,
        List<String> questionUuids
) {
}
