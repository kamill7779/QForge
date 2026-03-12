package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.internal.api.CreateQuestionFromParseRequest;
import io.github.kamill7779.qforge.internal.api.CreateQuestionFromParseResponse;
import io.github.kamill7779.qforge.internal.api.QuestionFullDTO;
import io.github.kamill7779.qforge.internal.api.QuestionSummaryDTO;
import io.github.kamill7779.qforge.question.entity.Answer;
import io.github.kamill7779.qforge.question.entity.AnswerAsset;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionAsset;
import io.github.kamill7779.qforge.question.entity.QuestionTagRel;
import io.github.kamill7779.qforge.question.entity.Tag;
import io.github.kamill7779.qforge.question.entity.TagCategory;
import io.github.kamill7779.qforge.question.repository.AnswerAssetRepository;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.repository.QuestionTagRelRepository;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import io.github.kamill7779.qforge.question.service.ParsedQuestionCreateService;
import io.github.kamill7779.qforge.question.service.QuestionSummaryQueryService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * question-core-service 内部 API —— 供 exam-service 和 exam-parse-service 通过 Feign 调用。
 * <p>
 * 不经过 gateway，服务间直接通信。
 */
@RestController
@RequestMapping("/internal/questions")
public class InternalQuestionController {

    private static final Logger log = LoggerFactory.getLogger(InternalQuestionController.class);

    private final QuestionSummaryQueryService questionSummaryQueryService;
    private final ParsedQuestionCreateService parsedQuestionCreateService;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final QuestionAssetRepository questionAssetRepository;
    private final AnswerAssetRepository answerAssetRepository;
    private final QuestionTagRelRepository questionTagRelRepository;
    private final TagRepository tagRepository;
    private final TagCategoryRepository tagCategoryRepository;

    public InternalQuestionController(
            QuestionSummaryQueryService questionSummaryQueryService,
            ParsedQuestionCreateService parsedQuestionCreateService,
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            QuestionAssetRepository questionAssetRepository,
            AnswerAssetRepository answerAssetRepository,
            QuestionTagRelRepository questionTagRelRepository,
            TagRepository tagRepository,
            TagCategoryRepository tagCategoryRepository) {
        this.questionSummaryQueryService = questionSummaryQueryService;
        this.parsedQuestionCreateService = parsedQuestionCreateService;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.questionAssetRepository = questionAssetRepository;
        this.answerAssetRepository = answerAssetRepository;
        this.questionTagRelRepository = questionTagRelRepository;
        this.tagRepository = tagRepository;
        this.tagCategoryRepository = tagCategoryRepository;
    }

    // ======================== 1. 批量获取摘要 ========================

    @GetMapping("/batch")
    public ResponseEntity<List<QuestionSummaryDTO>> batchGetSummaries(
            @RequestParam("uuids") String uuids,
            @RequestParam("ownerUser") String ownerUser) {

        List<String> uuidList = Arrays.stream(uuids.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (uuidList.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        return ResponseEntity.ok(questionSummaryQueryService.getSummaries(uuidList, ownerUser));
    }

    // ======================== 2. 批量获取完整数据 ========================

    @SuppressWarnings("unchecked")
    @PostMapping("/batch-full")
    public ResponseEntity<List<QuestionFullDTO>> batchGetFull(@RequestBody Map<String, Object> request) {
        List<String> uuidList = (List<String>) request.getOrDefault("uuids", List.of());
        String ownerUser = (String) request.getOrDefault("ownerUser", "");

        if (uuidList.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<Question> questions = questionRepository.findByQuestionUuidsAndOwnerUser(uuidList, ownerUser);
        List<Long> questionIds = questions.stream().map(Question::getId).toList();

        // 批量查关联数据
        List<Answer> allAnswers = answerRepository.findByQuestionIds(questionIds);
        List<QuestionAsset> allStemAssets = questionAssetRepository.findActiveByQuestionIds(questionIds);
        List<AnswerAsset> allAnswerAssets = answerAssetRepository.findByQuestionIds(questionIds);
        List<QuestionTagRel> allTagRels = questionTagRelRepository.findByQuestionIds(questionIds);

        // tag ID → Tag 实体
        List<Long> tagIds = allTagRels.stream().map(QuestionTagRel::getTagId).distinct().toList();
        Map<Long, Tag> tagMap = tagRepository.findByIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));

        // categoryCode → TagCategory
        List<TagCategory> mainCategories = tagCategoryRepository.findEnabledMainCategories();
        Map<String, TagCategory> categoryMap = mainCategories.stream()
                .collect(Collectors.toMap(TagCategory::getCategoryCode, c -> c));

        // 按 questionId 分组
        Map<Long, List<Answer>> answersByQ = allAnswers.stream()
                .collect(Collectors.groupingBy(Answer::getQuestionId));
        Map<Long, List<QuestionAsset>> stemAssetsByQ = allStemAssets.stream()
                .collect(Collectors.groupingBy(QuestionAsset::getQuestionId));
        Map<Long, List<AnswerAsset>> answerAssetsByA = allAnswerAssets.stream()
                .collect(Collectors.groupingBy(AnswerAsset::getAnswerId));
        Map<Long, List<QuestionTagRel>> tagRelsByQ = allTagRels.stream()
                .collect(Collectors.groupingBy(QuestionTagRel::getQuestionId));

        List<QuestionFullDTO> result = new ArrayList<>();

        for (Question q : questions) {
            // 题干资产
            List<QuestionFullDTO.AssetDTO> stemAssetDtos =
                    stemAssetsByQ.getOrDefault(q.getId(), List.of()).stream()
                            .map(a -> new QuestionFullDTO.AssetDTO(a.getRefKey(), a.getImageData(), a.getMimeType()))
                            .toList();

            // 答案
            List<QuestionFullDTO.AnswerDTO> answerDtos =
                    answersByQ.getOrDefault(q.getId(), List.of()).stream()
                            .map(ans -> {
                                List<QuestionFullDTO.AssetDTO> ansAssets =
                                        answerAssetsByA.getOrDefault(ans.getId(), List.of()).stream()
                                                .map(aa -> new QuestionFullDTO.AssetDTO(aa.getRefKey(), aa.getImageData(), aa.getMimeType()))
                                                .toList();
                                return new QuestionFullDTO.AnswerDTO(
                                        ans.getAnswerUuid(), ans.getLatexText(), ans.getSortOrder(),
                                        ans.isOfficial(), ansAssets);
                            })
                            .toList();

            // 主标签
            List<QuestionFullDTO.MainTagDTO> mainTagDtos = new ArrayList<>();
            List<String> secondaryTags = new ArrayList<>();
            for (QuestionTagRel rel : tagRelsByQ.getOrDefault(q.getId(), List.of())) {
                Tag tag = tagMap.get(rel.getTagId());
                if (tag == null) continue;
                TagCategory cat = categoryMap.get(rel.getCategoryCode());
                if (cat != null) {
                    mainTagDtos.add(new QuestionFullDTO.MainTagDTO(
                            tag.getCategoryCode(), cat.getCategoryName(),
                            tag.getTagCode(), tag.getTagName()));
                } else {
                    secondaryTags.add(tag.getTagName());
                }
            }

            result.add(new QuestionFullDTO(
                    q.getQuestionUuid(), q.getStemText(), q.getDifficulty(), q.getSource(),
                    answerDtos, stemAssetDtos, mainTagDtos, secondaryTags));
        }

        return ResponseEntity.ok(result);
    }

    // ======================== 3. 检查题目是否存在 ========================

    @GetMapping("/{questionUuid}/exists")
    public ResponseEntity<Map<String, Object>> checkExists(
            @PathVariable("questionUuid") String questionUuid,
            @RequestParam("ownerUser") String ownerUser) {

        boolean exists = questionRepository.findByQuestionUuidAndOwnerUser(questionUuid, ownerUser).isPresent();
        return ResponseEntity.ok(Map.of("exists", exists, "questionUuid", questionUuid));
    }

    // ======================== 4. 从解析结果创建正式题目 ========================

    @PostMapping("/from-parse")
    public ResponseEntity<CreateQuestionFromParseResponse> createFromParse(
            @RequestBody CreateQuestionFromParseRequest request) {
        CreateQuestionFromParseResponse response = parsedQuestionCreateService.create(request);
        log.info("Question created from parse: questionUuid={}", response.questionUuid());
        return ResponseEntity.ok(response);
    }
}
