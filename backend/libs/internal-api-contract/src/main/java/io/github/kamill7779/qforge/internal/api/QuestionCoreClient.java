package io.github.kamill7779.qforge.internal.api;

import java.util.List;
import java.util.Map;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * question-core-service 的内部 API Feign 客户端。
 * <p>
 * 由 exam-service 和 exam-parse-service 依赖，通过 Nacos 发现 question-core-service。
 */
@FeignClient(name = "question-core-service")
public interface QuestionCoreClient {

    /**
     * 批量获取题目摘要（stem、status、difficulty 等），用于试卷详情和试题篮展示。
     *
     * @param uuids     逗号分隔的 questionUuid 列表
     * @param ownerUser 数据归属用户
     */
    @GetMapping("/internal/questions/batch")
    List<QuestionSummaryDTO> batchGetSummaries(
            @RequestParam("uuids") String uuids,
            @RequestParam("ownerUser") String ownerUser
    );

    /**
     * 批量获取题目完整数据（含答案 + 资产 + 标签），用于 Word 导出。
     */
    @PostMapping("/internal/questions/batch-full")
    List<QuestionFullDTO> batchGetFull(
            @RequestBody Map<String, Object> request
    );

    /**
     * 校验题目是否存在且归属指定用户。
     */
    @GetMapping("/internal/questions/{questionUuid}/exists")
    Map<String, Object> checkExists(
            @PathVariable("questionUuid") String questionUuid,
            @RequestParam("ownerUser") String ownerUser
    );

    /**
     * 从解析结果创建正式题目（含答案 + 资产）。exam-parse-service 确认入库时调用。
     */
    @PostMapping("/internal/questions/from-parse")
    CreateQuestionFromParseResponse createFromParse(
            @RequestBody CreateQuestionFromParseRequest request
    );

    /**
     * 从高考数学语料库物化创建正式题目。gaokao-corpus-service 物化时调用。
     */
    @PostMapping("/internal/questions/from-gaokao")
    CreateQuestionFromGaokaoResponse createFromGaokao(
            @RequestBody CreateQuestionFromGaokaoRequest request
    );
}
