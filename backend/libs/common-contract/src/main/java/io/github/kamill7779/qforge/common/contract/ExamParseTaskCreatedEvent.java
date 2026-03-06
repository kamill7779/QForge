package io.github.kamill7779.qforge.common.contract;

import java.util.List;

/**
 * 试卷解析任务创建事件 —— question-service → ocr-service。
 * <p>
 * MQ 消息中不含文件 base64 正文（过大），仅携带 taskUuid + 文件元数据列表；
 * ocr-service 消费后从 DB（q_exam_parse_source_file）拉取实际文件数据。
 */
public record ExamParseTaskCreatedEvent(
        String taskUuid,
        String ownerUser,
        boolean hasAnswerHint,
        List<SourceFileMeta> files
) {

    /**
     * 源文件元数据（不含 base64 正文）。
     */
    public record SourceFileMeta(
            int fileIndex,
            String fileName,
            String fileType,
            int pageCount
    ) {
    }
}
