package io.github.kamill7779.qforge.internal.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * exam-parse-service 确认题目入库时提交给 question-core 的请求。
 */
public record CreateQuestionFromParseRequest(
        String ownerUser,
        String stemXml,
        String answerXml,
        BigDecimal difficulty,
        /** key → refKey, value → base64 image data */
        Map<String, String> stemImages,
        /** key → refKey, value → base64 image data */
        Map<String, String> answerImages,
        /** 主标签 JSON (原始解析结果) */
        String mainTagsJson,
        /** 二级标签 JSON */
        String secondaryTagsJson
) {}
