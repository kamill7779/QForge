package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionAnswer;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionAsset;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionMaterialization;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionProfile;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkAnswerAssetMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionAnswerMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionAssetMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMaterializationMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionProfileMapper;
import io.github.kamill7779.qforge.internal.api.CreateQuestionFromGaokaoRequest;
import io.github.kamill7779.qforge.internal.api.CreateQuestionFromGaokaoResponse;
import io.github.kamill7779.qforge.internal.api.QuestionCoreClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterializationServiceImpl implements MaterializationService {

    private static final Logger log = LoggerFactory.getLogger(MaterializationServiceImpl.class);

    private final GkQuestionMapper questionMapper;
    private final GkQuestionAnswerMapper questionAnswerMapper;
    private final GkQuestionAssetMapper questionAssetMapper;
    private final GkAnswerAssetMapper answerAssetMapper;
    private final GkQuestionProfileMapper questionProfileMapper;
    private final GkQuestionMaterializationMapper materializationMapper;
    private final QuestionCoreClient questionCoreClient;

    public MaterializationServiceImpl(
            GkQuestionMapper questionMapper,
            GkQuestionAnswerMapper questionAnswerMapper,
            GkQuestionAssetMapper questionAssetMapper,
            GkAnswerAssetMapper answerAssetMapper,
            GkQuestionProfileMapper questionProfileMapper,
            GkQuestionMaterializationMapper materializationMapper,
            QuestionCoreClient questionCoreClient
    ) {
        this.questionMapper = questionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionAssetMapper = questionAssetMapper;
        this.answerAssetMapper = answerAssetMapper;
        this.questionProfileMapper = questionProfileMapper;
        this.materializationMapper = materializationMapper;
        this.questionCoreClient = questionCoreClient;
    }

    @Override
    @Transactional
    public void materialize(Long gkQuestionId, String questionUuid, String ownerUser) {
        GkQuestion question = resolveQuestion(gkQuestionId, questionUuid);
        if (question == null) {
            throw new IllegalArgumentException("GK question not found");
        }

        CreateQuestionFromGaokaoRequest req = new CreateQuestionFromGaokaoRequest();
        req.setOwnerUser(ownerUser);
        req.setQuestionTypeCode(question.getQuestionTypeCode());
        req.setStemText(question.getStemText());
        req.setStemXml(question.getStemXml());
        req.setSource("GAOKAO_CORPUS");
        req.setDifficultyLevel(question.getDifficultyLevel());
        req.setDifficulty(question.getDifficultyScore());
        applyAnswerPayload(question, req);
        applyTagPayload(question, req);
        applyAssetPayload(question, req);

        CreateQuestionFromGaokaoResponse resp = questionCoreClient.createFromGaokao(req);

        if (resp != null && resp.isSuccess()) {
            GkQuestionMaterialization mat = new GkQuestionMaterialization();
            mat.setGkQuestionId(question.getId());
            mat.setTargetQuestionUuid(resp.getQuestionUuid());
            mat.setOwnerUser(ownerUser);
            mat.setMode("COPY");
            mat.setStatus("ACTIVE");
            mat.setCreatedAt(LocalDateTime.now());
            mat.setUpdatedAt(LocalDateTime.now());
            materializationMapper.insert(mat);
            log.info("Materialized gkQuestion={} questionUuid={} -> targetUuid={}", question.getId(), question.getQuestionUuid(), resp.getQuestionUuid());
        } else {
            String errorMsg = resp != null ? resp.getErrorMessage() : "null response";
            log.error("Materialization failed for gkQuestion={} questionUuid={}: {}", question.getId(), question.getQuestionUuid(), errorMsg);
            throw new RuntimeException("Materialization failed: " + errorMsg);
        }
    }

    private GkQuestion resolveQuestion(Long gkQuestionId, String questionUuid) {
        if (gkQuestionId != null) {
            return questionMapper.selectById(gkQuestionId);
        }
        if (questionUuid != null && !questionUuid.isBlank()) {
            return questionMapper.selectOne(
                    new LambdaQueryWrapper<GkQuestion>()
                            .eq(GkQuestion::getQuestionUuid, questionUuid));
        }
        throw new IllegalArgumentException("Either gkQuestionId or questionUuid is required");
    }

    private void applyAnswerPayload(GkQuestion question, CreateQuestionFromGaokaoRequest request) {
        List<GkQuestionAnswer> answers = questionAnswerMapper.selectList(
                new LambdaQueryWrapper<GkQuestionAnswer>()
                        .eq(GkQuestionAnswer::getQuestionId, question.getId())
                        .eq(GkQuestionAnswer::getIsOfficial, true)
                        .orderByAsc(GkQuestionAnswer::getSortOrder));
        if (answers.isEmpty()) {
            return;
        }
        GkQuestionAnswer answer = answers.get(0);
        request.setAnswerText(answer.getAnswerText());
        request.setAnswerXml(answer.getAnswerXml());
    }

    private void applyTagPayload(GkQuestion question, CreateQuestionFromGaokaoRequest request) {
        GkQuestionProfile profile = questionProfileMapper.selectOne(
                new LambdaQueryWrapper<GkQuestionProfile>()
                        .eq(GkQuestionProfile::getQuestionId, question.getId())
                        .orderByDesc(GkQuestionProfile::getProfileVersion)
                        .last("LIMIT 1"));
        if (profile == null) {
            request.setSecondaryTags(List.of());
            request.setMainTags(List.of());
            return;
        }

        List<CreateQuestionFromGaokaoRequest.TagEntry> mainTags = new ArrayList<>();
        appendMainTags(mainTags, "KNOWLEDGE", profile.getKnowledgePathJson());
        appendMainTags(mainTags, "METHOD", profile.getMethodTagsJson());
        appendMainTags(mainTags, "FORMULA", profile.getFormulaTagsJson());
        request.setMainTags(mainTags);

        List<String> secondaryTags = new ArrayList<>();
        secondaryTags.addAll(parseJsonTokens(profile.getAbilityTagsJson()));
        secondaryTags.addAll(parseJsonTokens(profile.getMistakeTagsJson()));
        request.setSecondaryTags(secondaryTags);
    }

    private void applyAssetPayload(GkQuestion question, CreateQuestionFromGaokaoRequest request) {
        List<GkQuestionAsset> stemAssets = questionAssetMapper.selectList(
                new LambdaQueryWrapper<GkQuestionAsset>()
                        .eq(GkQuestionAsset::getQuestionId, question.getId())
                        .orderByAsc(GkQuestionAsset::getSortOrder));
        request.setStemAssets(stemAssets.stream().map(asset -> {
            CreateQuestionFromGaokaoRequest.AssetEntry entry = new CreateQuestionFromGaokaoRequest.AssetEntry();
            entry.setAssetType(asset.getAssetType());
            entry.setStorageRef(asset.getStorageRef());
            entry.setRefKey("stem-" + asset.getId());
            return entry;
        }).toList());

        List<GkQuestionAnswer> answers = questionAnswerMapper.selectList(
                new LambdaQueryWrapper<GkQuestionAnswer>()
                        .eq(GkQuestionAnswer::getQuestionId, question.getId())
                        .eq(GkQuestionAnswer::getIsOfficial, true)
                        .orderByAsc(GkQuestionAnswer::getSortOrder));
        if (answers.isEmpty()) {
            request.setAnswerAssets(List.of());
            return;
        }
        GkQuestionAnswer answer = answers.get(0);
        request.setAnswerAssets(answerAssetMapper.selectList(
                        new LambdaQueryWrapper<io.github.kamill7779.qforge.gaokaocorpus.entity.GkAnswerAsset>()
                                .eq(io.github.kamill7779.qforge.gaokaocorpus.entity.GkAnswerAsset::getAnswerId, answer.getId())
                                .orderByAsc(io.github.kamill7779.qforge.gaokaocorpus.entity.GkAnswerAsset::getSortOrder))
                .stream()
                .map(asset -> {
                    CreateQuestionFromGaokaoRequest.AssetEntry entry = new CreateQuestionFromGaokaoRequest.AssetEntry();
                    entry.setAssetType(asset.getAssetType());
                    entry.setStorageRef(asset.getStorageRef());
                    entry.setRefKey("answer-" + asset.getId());
                    return entry;
                })
                .toList());
    }

    private void appendMainTags(List<CreateQuestionFromGaokaoRequest.TagEntry> result, String categoryCode, String rawJson) {
        for (String token : parseJsonTokens(rawJson)) {
            CreateQuestionFromGaokaoRequest.TagEntry entry = new CreateQuestionFromGaokaoRequest.TagEntry();
            entry.setCategoryCode(categoryCode);
            entry.setTagCode(token);
            result.add(entry);
        }
    }

    private List<String> parseJsonTokens(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }
        String trimmed = rawJson.trim();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1);
        }
        List<String> tokens = new ArrayList<>();
        for (String token : trimmed.split("[,，]")) {
            String normalized = token.replace("\"", "").trim();
            if (!normalized.isEmpty()) {
                tokens.add(normalized);
            }
        }
        return tokens;
    }
}
