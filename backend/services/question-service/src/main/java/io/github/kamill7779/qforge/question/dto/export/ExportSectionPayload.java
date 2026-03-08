package io.github.kamill7779.qforge.question.dto.export;

import java.util.List;

/**
 * 导出分区定义。
 */
public record ExportSectionPayload(
        String title,
        List<String> questionUuids
) {
}
