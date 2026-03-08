package io.github.kamill7779.qforge.question.service;

import feign.Response;
import io.github.kamill7779.qforge.question.client.ExportSidecarClient;
import io.github.kamill7779.qforge.question.dto.ExportWordRequest;
import io.github.kamill7779.qforge.question.dto.QuestionMainTagResponse;
import io.github.kamill7779.qforge.question.dto.export.ExportAnswerPayload;
import io.github.kamill7779.qforge.question.dto.export.ExportAssetPayload;
import io.github.kamill7779.qforge.question.dto.export.ExportQuestionPayload;
import io.github.kamill7779.qforge.question.dto.export.ExportSectionPayload;
import io.github.kamill7779.qforge.question.dto.export.ExportSidecarRequest;
import io.github.kamill7779.qforge.question.entity.Answer;
import io.github.kamill7779.qforge.question.entity.AnswerAsset;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionAsset;
import io.github.kamill7779.qforge.question.entity.QuestionTagRel;
import io.github.kamill7779.qforge.question.entity.Tag;
import io.github.kamill7779.qforge.question.entity.TagCategory;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.repository.AnswerAssetRepository;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.repository.QuestionTagRelRepository;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 导出服务 — 组装题目完整数据，调用 export-sidecar 生成 Word。
 *
 * 流程: 前端 → Gateway → QuestionController → ExportService
 *       → (组装数据) → ExportSidecarClient(Feign) → Word bytes → 返回前端
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);
    private static final String CATEGORY_SECONDARY_CUSTOM = "SECONDARY_CUSTOM";

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionAssetRepository questionAssetRepository;
    private final AnswerAssetRepository answerAssetRepository;
    private final QuestionTagRelRepository questionTagRelRepository;
    private final TagRepository tagRepository;
    private final TagCategoryRepository tagCategoryRepository;
    private final ExportSidecarClient exportSidecarClient;

    public ExportService(
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            QuestionAssetRepository questionAssetRepository,
            AnswerAssetRepository answerAssetRepository,
            QuestionTagRelRepository questionTagRelRepository,
            TagRepository tagRepository,
            TagCategoryRepository tagCategoryRepository,
            ExportSidecarClient exportSidecarClient
    ) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.questionAssetRepository = questionAssetRepository;
        this.answerAssetRepository = answerAssetRepository;
        this.questionTagRelRepository = questionTagRelRepository;
        this.tagRepository = tagRepository;
        this.tagCategoryRepository = tagCategoryRepository;
        this.exportSidecarClient = exportSidecarClient;
    }

    /**
     * 导出指定题目为 Word 文档。
     *
     * @param request     前端请求（UUID 列表 + 选项）
     * @param requestUser 当前用户
     * @return Word 文档字节数组
     */
    public byte[] exportQuestionsWord(ExportWordRequest request, String requestUser) {
        // 1. 收集所有 UUID
        List<String> allUuids = collectAllUuids(request);
        if (allUuids.isEmpty()) {
            throw new BusinessValidationException(
                    "EXPORT_NO_QUESTIONS", "没有指定要导出的题目",
                    Map.of(), HttpStatus.BAD_REQUEST);
        }

        // 2. 批量查询题目
        List<Question> questions = questionRepository.findByQuestionUuidsAndOwnerUser(allUuids, requestUser);
        if (questions.isEmpty()) {
            throw new BusinessValidationException(
                    "EXPORT_QUESTIONS_NOT_FOUND", "未找到任何题目",
                    Map.of("requestUser", requestUser), HttpStatus.NOT_FOUND);
        }

        Map<String, Question> uuidToQuestion = questions.stream()
                .collect(Collectors.toMap(Question::getQuestionUuid, q -> q));
        List<Long> questionIds = questions.stream().map(Question::getId).toList();

        // 3. 批量加载关联数据
        List<Answer> allAnswers = answerRepository.findByQuestionIds(questionIds);
        List<QuestionAsset> allStemAssets = questionAssetRepository.findActiveByQuestionIds(questionIds);
        List<AnswerAsset> allAnswerAssets = answerAssetRepository.findByQuestionIds(questionIds);

        // 按 questionId 分组
        Map<Long, List<Answer>> answersByQuestionId = new HashMap<>();
        for (Answer a : allAnswers) {
            answersByQuestionId.computeIfAbsent(a.getQuestionId(), k -> new ArrayList<>()).add(a);
        }

        Map<Long, List<QuestionAsset>> stemAssetsByQuestionId = new HashMap<>();
        for (QuestionAsset qa : allStemAssets) {
            stemAssetsByQuestionId.computeIfAbsent(qa.getQuestionId(), k -> new ArrayList<>()).add(qa);
        }

        // 答案资产按 answerId 分组
        Map<Long, List<AnswerAsset>> answerAssetsByAnswerId = new HashMap<>();
        for (AnswerAsset aa : allAnswerAssets) {
            answerAssetsByAnswerId.computeIfAbsent(aa.getAnswerId(), k -> new ArrayList<>()).add(aa);
        }

        // 4. 加载标签数据
        Map<Long, TagData> tagDataMap = loadTagData(questionIds);

        // 5. 组装 ExportQuestionPayload 列表
        List<ExportQuestionPayload> payloads = new ArrayList<>();
        for (String uuid : allUuids) {
            Question q = uuidToQuestion.get(uuid);
            if (q == null) continue;

            // 题干资产
            List<ExportAssetPayload> stemAssetPayloads = stemAssetsByQuestionId
                    .getOrDefault(q.getId(), List.of()).stream()
                    .filter(a -> a.getRefKey() != null)
                    .map(a -> new ExportAssetPayload(a.getRefKey(), a.getImageData(), a.getMimeType()))
                    .toList();

            // 答案 + 答案资产
            List<ExportAnswerPayload> ansPayloads = new ArrayList<>();
            for (Answer ans : answersByQuestionId.getOrDefault(q.getId(), List.of())) {
                List<ExportAssetPayload> ansAssets = answerAssetsByAnswerId
                        .getOrDefault(ans.getId(), List.of()).stream()
                        .filter(a -> a.getRefKey() != null)
                        .map(a -> new ExportAssetPayload(a.getRefKey(), a.getImageData(), a.getMimeType()))
                        .toList();
                ansPayloads.add(new ExportAnswerPayload(
                        ans.getAnswerUuid(), ans.getLatexText(), ans.getSortOrder(), ansAssets));
            }

            // 标签
            TagData td = tagDataMap.getOrDefault(q.getId(), TagData.EMPTY);

            payloads.add(new ExportQuestionPayload(
                    q.getQuestionUuid(),
                    q.getStemText(),
                    q.getDifficulty() != null ? q.getDifficulty().doubleValue() : null,
                    ansPayloads,
                    stemAssetPayloads,
                    td.mainTagMaps(),
                    td.secondaryTags()
            ));
        }

        // 6. 构建 sidecar 请求
        List<ExportSectionPayload> sectionPayloads = null;
        if (request.sections() != null && !request.sections().isEmpty()) {
            sectionPayloads = request.sections().stream()
                    .map(s -> new ExportSectionPayload(s.title(), s.questionUuids()))
                    .toList();
        }

        ExportSidecarRequest sidecarRequest = new ExportSidecarRequest(
                payloads,
                request.safeTitle(),
                request.includeAnswers(),
                request.safeAnswerPosition(),
                sectionPayloads
        );

        // 7. 调用 export-sidecar
        try {
            Response feignResponse = exportSidecarClient.exportQuestionsWord(sidecarRequest);
            if (feignResponse.status() != 200) {
                log.error("export-sidecar returned status {}", feignResponse.status());
                throw new BusinessValidationException(
                        "EXPORT_SIDECAR_ERROR", "导出服务返回错误: " + feignResponse.status(),
                        Map.of(), HttpStatus.BAD_GATEWAY);
            }
            return feignResponse.body().asInputStream().readAllBytes();
        } catch (BusinessValidationException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to read export-sidecar response", e);
            throw new BusinessValidationException(
                    "EXPORT_IO_ERROR", "读取导出文件失败",
                    Map.of(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("export-sidecar call failed", e);
            throw new BusinessValidationException(
                    "EXPORT_SIDECAR_UNAVAILABLE", "导出服务不可用: " + e.getMessage(),
                    Map.of(), HttpStatus.BAD_GATEWAY);
        }
    }

    // ──────────────────────────────────────────────

    private List<String> collectAllUuids(ExportWordRequest request) {
        if (request.sections() != null && !request.sections().isEmpty()) {
            List<String> uuids = new ArrayList<>();
            for (ExportSectionPayload s : request.sections()) {
                uuids.addAll(s.questionUuids());
            }
            return uuids;
        }
        return request.questionUuids() != null ? request.questionUuids() : List.of();
    }

    /**
     * 批量加载题目标签数据（主标签 + 二级标签）。
     */
    private Map<Long, TagData> loadTagData(List<Long> questionIds) {
        List<TagCategory> mainCategories = tagCategoryRepository.findEnabledMainCategories();
        List<QuestionTagRel> rels = questionTagRelRepository.findByQuestionIds(questionIds);

        if (rels.isEmpty()) {
            // 所有题目都没有标签 → 返回空
            return questionIds.stream()
                    .collect(Collectors.toMap(id -> id, id -> TagData.EMPTY));
        }

        Set<Long> tagIds = rels.stream().map(QuestionTagRel::getTagId).collect(Collectors.toSet());
        Map<Long, Tag> tagMap = tagRepository.findByIds(new ArrayList<>(tagIds)).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));

        Map<String, TagCategory> mainCategoryMap = mainCategories.stream()
                .collect(Collectors.toMap(TagCategory::getCategoryCode, c -> c));

        Map<Long, Map<String, String>> mainTagByQuestion = new HashMap<>();
        Map<Long, List<String>> secondaryByQuestion = new HashMap<>();

        for (QuestionTagRel rel : rels) {
            Tag tag = tagMap.get(rel.getTagId());
            if (tag == null) continue;

            if (CATEGORY_SECONDARY_CUSTOM.equals(rel.getCategoryCode())) {
                secondaryByQuestion.computeIfAbsent(rel.getQuestionId(), k -> new ArrayList<>())
                        .add(tag.getTagName());
            } else {
                TagCategory cat = mainCategoryMap.get(rel.getCategoryCode());
                if (cat != null) {
                    mainTagByQuestion.computeIfAbsent(rel.getQuestionId(), k -> new LinkedHashMap<>())
                            .put(cat.getCategoryCode(), tag.getTagName());
                }
            }
        }

        Map<Long, TagData> result = new HashMap<>();
        for (Long qid : questionIds) {
            List<Map<String, String>> mainMaps = new ArrayList<>();
            Map<String, String> mainMap = mainTagByQuestion.getOrDefault(qid, Map.of());
            for (TagCategory cat : mainCategories) {
                String tagName = mainMap.getOrDefault(cat.getCategoryCode(), "");
                mainMaps.add(Map.of(
                        "categoryCode", cat.getCategoryCode(),
                        "categoryName", cat.getCategoryName(),
                        "tagName", tagName
                ));
            }
            result.put(qid, new TagData(
                    mainMaps,
                    secondaryByQuestion.getOrDefault(qid, List.of())
            ));
        }
        return result;
    }

    private record TagData(
            List<Map<String, String>> mainTagMaps,
            List<String> secondaryTags
    ) {
        static final TagData EMPTY = new TagData(List.of(), List.of());
    }
}
