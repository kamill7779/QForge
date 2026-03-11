package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.kamill7779.qforge.common.contract.GaokaoIndexingConstants;
import io.github.kamill7779.qforge.common.contract.GaokaoPaperIndexRequestedEvent;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkQuestionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftAnswer;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftAnswerAsset;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftOption;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftPaper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftProfilePreview;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftQuestionAsset;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftSection;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkAnswerAsset;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSession;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkPaper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkPaperSection;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionAnswer;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionAsset;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionOption;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionProfile;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkAnswerAssetMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftAnswerAssetMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftAnswerMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftQuestionAssetMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftOptionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftProfilePreviewMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftSectionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionAssetMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSessionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkPaperSectionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionAnswerMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionOptionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionProfileMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublishServiceImpl implements PublishService {

    private static final Logger log = LoggerFactory.getLogger(PublishServiceImpl.class);

    private final GkDraftPaperMapper draftPaperMapper;
    private final GkDraftSectionMapper draftSectionMapper;
    private final GkDraftQuestionMapper draftQuestionMapper;
    private final GkDraftOptionMapper draftOptionMapper;
    private final GkDraftAnswerMapper draftAnswerMapper;
    private final GkDraftQuestionAssetMapper draftQuestionAssetMapper;
    private final GkDraftAnswerAssetMapper draftAnswerAssetMapper;
    private final GkDraftProfilePreviewMapper draftProfilePreviewMapper;
    private final GkIngestSessionMapper ingestSessionMapper;
    private final GkPaperMapper paperMapper;
    private final GkPaperSectionMapper paperSectionMapper;
    private final GkQuestionMapper questionMapper;
    private final GkQuestionOptionMapper questionOptionMapper;
    private final GkQuestionAnswerMapper questionAnswerMapper;
    private final GkQuestionAssetMapper questionAssetMapper;
    private final GkAnswerAssetMapper answerAssetMapper;
    private final GkQuestionProfileMapper questionProfileMapper;
    private final RabbitTemplate rabbitTemplate;

    public PublishServiceImpl(
            GkDraftPaperMapper draftPaperMapper,
            GkDraftSectionMapper draftSectionMapper,
            GkDraftQuestionMapper draftQuestionMapper,
            GkDraftOptionMapper draftOptionMapper,
            GkDraftAnswerMapper draftAnswerMapper,
            GkDraftQuestionAssetMapper draftQuestionAssetMapper,
            GkDraftAnswerAssetMapper draftAnswerAssetMapper,
            GkDraftProfilePreviewMapper draftProfilePreviewMapper,
            GkIngestSessionMapper ingestSessionMapper,
            GkPaperMapper paperMapper,
            GkPaperSectionMapper paperSectionMapper,
            GkQuestionMapper questionMapper,
            GkQuestionOptionMapper questionOptionMapper,
            GkQuestionAnswerMapper questionAnswerMapper,
            GkQuestionAssetMapper questionAssetMapper,
            GkAnswerAssetMapper answerAssetMapper,
            GkQuestionProfileMapper questionProfileMapper,
            RabbitTemplate rabbitTemplate
    ) {
        this.draftPaperMapper = draftPaperMapper;
        this.draftSectionMapper = draftSectionMapper;
        this.draftQuestionMapper = draftQuestionMapper;
        this.draftOptionMapper = draftOptionMapper;
        this.draftAnswerMapper = draftAnswerMapper;
        this.draftQuestionAssetMapper = draftQuestionAssetMapper;
        this.draftAnswerAssetMapper = draftAnswerAssetMapper;
        this.draftProfilePreviewMapper = draftProfilePreviewMapper;
        this.ingestSessionMapper = ingestSessionMapper;
        this.paperMapper = paperMapper;
        this.paperSectionMapper = paperSectionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionAssetMapper = questionAssetMapper;
        this.answerAssetMapper = answerAssetMapper;
        this.questionProfileMapper = questionProfileMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public GkPaperDTO publishPaper(String draftPaperUuid) {
        GkDraftPaper draftPaper = draftPaperMapper.selectOne(
                new LambdaQueryWrapper<GkDraftPaper>()
                        .eq(GkDraftPaper::getDraftPaperUuid, draftPaperUuid));
        if (draftPaper == null) {
            throw new IllegalArgumentException("Draft paper not found: " + draftPaperUuid);
        }
        GkPaper existingPaper = paperMapper.selectOne(
                new LambdaQueryWrapper<GkPaper>()
                        .eq(GkPaper::getDraftPaperId, draftPaper.getId())
                        .last("LIMIT 1"));
        if (existingPaper != null) {
            log.info("Skip duplicate publish for draftPaperUuid={}, paperUuid={}", draftPaperUuid, existingPaper.getPaperUuid());
            return toPaperDTO(existingPaper);
        }

        // Get session UUID
        GkIngestSession session = ingestSessionMapper.selectById(draftPaper.getSessionId());
        String sessionUuid = session != null ? session.getSessionUuid() : null;

        // 1. Create published paper
        GkPaper paper = new GkPaper();
        paper.setPaperUuid(UUID.randomUUID().toString());
        paper.setSourceSessionUuid(sessionUuid);
        paper.setDraftPaperId(draftPaper.getId());
        paper.setPaperName(draftPaper.getPaperName());
        paper.setPaperTypeCode(draftPaper.getPaperTypeCode());
        paper.setExamYear(draftPaper.getExamYear());
        paper.setProvinceCode(draftPaper.getProvinceCode());
        paper.setSubjectCode("MATH");
        paper.setStatus("INDEXING");
        paper.setCreatedAt(LocalDateTime.now());
        paper.setUpdatedAt(LocalDateTime.now());
        paperMapper.insert(paper);
        log.info("Published paper: uuid={}", paper.getPaperUuid());

        // 2. Copy sections
        List<GkDraftSection> draftSections = draftSectionMapper.selectList(
                new LambdaQueryWrapper<GkDraftSection>()
                        .eq(GkDraftSection::getDraftPaperId, draftPaper.getId())
                        .orderByAsc(GkDraftSection::getSortOrder));
        Map<Long, Long> sectionIdMap = new HashMap<>();
        for (GkDraftSection ds : draftSections) {
            GkPaperSection ps = new GkPaperSection();
            ps.setSectionUuid(UUID.randomUUID().toString());
            ps.setPaperId(paper.getId());
            ps.setSectionCode(ds.getSectionCode());
            ps.setSectionTitle(ds.getSectionTitle());
            ps.setSortOrder(ds.getSortOrder());
            ps.setCreatedAt(LocalDateTime.now());
            paperSectionMapper.insert(ps);
            sectionIdMap.put(ds.getId(), ps.getId());
        }

        // 3. Copy questions
        List<GkDraftQuestion> draftQuestions = draftQuestionMapper.selectList(
                new LambdaQueryWrapper<GkDraftQuestion>()
                        .eq(GkDraftQuestion::getDraftPaperId, draftPaper.getId()));
        Map<Long, Long> questionIdMap = new HashMap<>();
        List<GkQuestionDTO> questionDTOs = new ArrayList<>();
        List<GaokaoPaperIndexRequestedEvent.QuestionPayload> eventQuestions = new ArrayList<>();

        for (GkDraftQuestion dq : draftQuestions) {
            GkQuestion q = new GkQuestion();
            q.setQuestionUuid(UUID.randomUUID().toString());
            q.setPaperId(paper.getId());
            q.setSectionId(dq.getDraftSectionId() != null ? sectionIdMap.get(dq.getDraftSectionId()) : null);
            q.setQuestionNo(dq.getQuestionNo());
            q.setQuestionTypeCode(dq.getQuestionTypeCode());
            q.setAnswerMode(dq.getAnswerMode());
            q.setStemText(dq.getStemText());
            q.setStemXml(dq.getStemXml());
            q.setNormalizedStemText(dq.getNormalizedStemText());
            q.setScore(dq.getScore());
            q.setHasAnswer(dq.getHasAnswer());
            q.setPublishedAt(LocalDateTime.now());
            q.setCreatedAt(LocalDateTime.now());
            q.setUpdatedAt(LocalDateTime.now());

            // Apply profile preview data if confirmed
            GkDraftProfilePreview preview = draftProfilePreviewMapper.selectOne(
                    new LambdaQueryWrapper<GkDraftProfilePreview>()
                            .eq(GkDraftProfilePreview::getDraftQuestionId, dq.getId())
                            .eq(GkDraftProfilePreview::getConfirmed, true)
                            .orderByDesc(GkDraftProfilePreview::getProfileVersion)
                            .last("LIMIT 1"));
            if (preview != null) {
                q.setDifficultyScore(preview.getDifficultyScore());
                q.setDifficultyLevel(preview.getDifficultyLevel());
            }

            questionMapper.insert(q);
            questionIdMap.put(dq.getId(), q.getId());

            // 4. Copy options
            List<GkDraftOption> options = draftOptionMapper.selectList(
                    new LambdaQueryWrapper<GkDraftOption>()
                            .eq(GkDraftOption::getDraftQuestionId, dq.getId())
                            .orderByAsc(GkDraftOption::getSortOrder));
            for (GkDraftOption opt : options) {
                GkQuestionOption qo = new GkQuestionOption();
                qo.setOptionUuid(UUID.randomUUID().toString());
                qo.setQuestionId(q.getId());
                qo.setOptionLabel(opt.getOptionLabel());
                qo.setOptionText(opt.getOptionText());
                qo.setOptionXml(opt.getOptionXml());
                qo.setIsCorrect(false);
                qo.setSortOrder(opt.getSortOrder());
                questionOptionMapper.insert(qo);
            }

            List<GkDraftQuestionAsset> stemAssets = draftQuestionAssetMapper.selectList(
                    new LambdaQueryWrapper<GkDraftQuestionAsset>()
                            .eq(GkDraftQuestionAsset::getDraftQuestionId, dq.getId())
                            .orderByAsc(GkDraftQuestionAsset::getSortOrder));
            for (GkDraftQuestionAsset stemAsset : stemAssets) {
                GkQuestionAsset asset = new GkQuestionAsset();
                asset.setQuestionId(q.getId());
                asset.setAssetType(stemAsset.getAssetType());
                asset.setStorageRef(stemAsset.getStorageRef());
                asset.setSortOrder(stemAsset.getSortOrder());
                questionAssetMapper.insert(asset);
            }

            // 5. Copy answers
            List<GkDraftAnswer> answers = draftAnswerMapper.selectList(
                    new LambdaQueryWrapper<GkDraftAnswer>()
                            .eq(GkDraftAnswer::getDraftQuestionId, dq.getId())
                            .orderByAsc(GkDraftAnswer::getSortOrder));
            List<GaokaoPaperIndexRequestedEvent.AnswerPayload> eventAnswers = new ArrayList<>();
            for (GkDraftAnswer ans : answers) {
                GkQuestionAnswer qa = new GkQuestionAnswer();
                qa.setAnswerUuid(UUID.randomUUID().toString());
                qa.setQuestionId(q.getId());
                qa.setAnswerType(ans.getAnswerType());
                qa.setAnswerText(ans.getAnswerText());
                qa.setAnswerXml(ans.getAnswerXml());
                qa.setIsOfficial(ans.getIsOfficial());
                qa.setSortOrder(ans.getSortOrder());
                questionAnswerMapper.insert(qa);

                List<GkDraftAnswerAsset> answerAssets = draftAnswerAssetMapper.selectList(
                        new LambdaQueryWrapper<GkDraftAnswerAsset>()
                                .eq(GkDraftAnswerAsset::getDraftAnswerId, ans.getId())
                                .orderByAsc(GkDraftAnswerAsset::getSortOrder));
                for (GkDraftAnswerAsset answerAsset : answerAssets) {
                    GkAnswerAsset asset = new GkAnswerAsset();
                    asset.setAnswerId(qa.getId());
                    asset.setAssetType(answerAsset.getAssetType());
                    asset.setStorageRef(answerAsset.getStorageRef());
                    asset.setSortOrder(answerAsset.getSortOrder());
                    answerAssetMapper.insert(asset);
                }
                eventAnswers.add(new GaokaoPaperIndexRequestedEvent.AnswerPayload(
                        qa.getId(),
                        qa.getAnswerText(),
                        qa.getAnswerXml(),
                        qa.getIsOfficial(),
                        qa.getSortOrder()
                ));
            }

            // 6. Copy profile
            if (preview != null) {
                GkQuestionProfile profile = new GkQuestionProfile();
                profile.setQuestionId(q.getId());
                profile.setKnowledgePathJson(preview.getKnowledgeTagsJson());
                profile.setMethodTagsJson(preview.getMethodTagsJson());
                profile.setAbilityTagsJson(preview.getAbilityTagsJson());
                profile.setMistakeTagsJson(preview.getMistakeTagsJson());
                profile.setFormulaTagsJson(preview.getFormulaTagsJson());
                profile.setReasoningStepsJson(preview.getReasoningStepsJson());
                profile.setAnalysisSummaryText(preview.getAnalysisSummaryText());
                profile.setDifficultyScore(preview.getDifficultyScore());
                profile.setDifficultyLevel(preview.getDifficultyLevel());
                profile.setProfileVersion(preview.getProfileVersion());
                profile.setCreatedAt(LocalDateTime.now());
                profile.setUpdatedAt(LocalDateTime.now());
                questionProfileMapper.insert(profile);
            }

            eventQuestions.add(new GaokaoPaperIndexRequestedEvent.QuestionPayload(
                    q.getId(),
                    q.getQuestionUuid(),
                    q.getQuestionNo(),
                    q.getQuestionTypeCode(),
                    q.getAnswerMode(),
                    q.getStemText(),
                    q.getStemXml(),
                    q.getNormalizedStemText(),
                    q.getDifficultyScore(),
                    q.getDifficultyLevel(),
                    preview != null ? preview.getKnowledgeTagsJson() : "[]",
                    preview != null ? preview.getMethodTagsJson() : "[]",
                    preview != null ? preview.getFormulaTagsJson() : "[]",
                    preview != null ? preview.getMistakeTagsJson() : "[]",
                    preview != null ? preview.getAbilityTagsJson() : "[]",
                    preview != null ? preview.getReasoningStepsJson() : "[]",
                    preview != null ? preview.getAnalysisSummaryText() : null,
                    eventAnswers
            ));

            GkQuestionDTO qDTO = new GkQuestionDTO();
            qDTO.setQuestionUuid(q.getQuestionUuid());
            qDTO.setPaperUuid(paper.getPaperUuid());
            qDTO.setQuestionNo(q.getQuestionNo());
            qDTO.setQuestionTypeCode(q.getQuestionTypeCode());
            qDTO.setStemText(q.getStemText());
            qDTO.setDifficultyScore(q.getDifficultyScore());
            qDTO.setDifficultyLevel(q.getDifficultyLevel());
            qDTO.setHasAnswer(q.getHasAnswer());
            questionDTOs.add(qDTO);
        }

        for (GkDraftQuestion dq : draftQuestions) {
            if (dq.getParentQuestionId() == null && dq.getRootQuestionId() == null) {
                continue;
            }
            Long publishedQuestionId = questionIdMap.get(dq.getId());
            GkQuestion publishedQuestion = publishedQuestionId == null ? null : questionMapper.selectById(publishedQuestionId);
            if (publishedQuestion == null) {
                continue;
            }
            publishedQuestion.setParentQuestionId(questionIdMap.get(dq.getParentQuestionId()));
            publishedQuestion.setRootQuestionId(questionIdMap.get(dq.getRootQuestionId()));
            questionMapper.updateById(publishedQuestion);
        }

        // 7. Update session and draft paper status
        draftPaper.setStatus("READY_TO_PUBLISH");
        draftPaper.setUpdatedAt(LocalDateTime.now());
        draftPaperMapper.updateById(draftPaper);
        if (session != null) {
            session.setStatus("PUBLISHED");
            session.setUpdatedAt(LocalDateTime.now());
            ingestSessionMapper.updateById(session);
        }

        rabbitTemplate.convertAndSend(
                GaokaoIndexingConstants.GAOKAO_INDEX_EXCHANGE,
                GaokaoIndexingConstants.ROUTING_PAPER_INDEX_REQUESTED,
                new GaokaoPaperIndexRequestedEvent(
                        UUID.randomUUID().toString(),
                        paper.getId(),
                        paper.getPaperUuid(),
                        paper.getPaperName(),
                        paper.getExamYear() != null ? String.valueOf(paper.getExamYear()) : null,
                        paper.getProvinceCode(),
                        LocalDateTime.now().toString(),
                        eventQuestions
                )
        );

        log.info("Published paper complete: uuid={}, questions={}", paper.getPaperUuid(), questionDTOs.size());

        GkPaperDTO dto = toPaperDTO(paper);
        dto.setQuestions(questionDTOs);
        return dto;
    }

    private GkPaperDTO toPaperDTO(GkPaper paper) {
        GkPaperDTO dto = new GkPaperDTO();
        dto.setPaperUuid(paper.getPaperUuid());
        dto.setPaperName(paper.getPaperName());
        dto.setPaperTypeCode(paper.getPaperTypeCode());
        dto.setExamYear(paper.getExamYear());
        dto.setProvinceCode(paper.getProvinceCode());
        dto.setSubjectCode(paper.getSubjectCode());
        dto.setStatus(paper.getStatus());
        List<GkQuestionDTO> questionDTOs = questionMapper.selectList(
                        new LambdaQueryWrapper<GkQuestion>()
                                .eq(GkQuestion::getPaperId, paper.getId())
                                .orderByAsc(GkQuestion::getId))
                .stream()
                .map(question -> {
                    GkQuestionDTO qDTO = new GkQuestionDTO();
                    qDTO.setQuestionUuid(question.getQuestionUuid());
                    qDTO.setPaperUuid(paper.getPaperUuid());
                    qDTO.setQuestionNo(question.getQuestionNo());
                    qDTO.setQuestionTypeCode(question.getQuestionTypeCode());
                    qDTO.setStemText(question.getStemText());
                    qDTO.setDifficultyScore(question.getDifficultyScore());
                    qDTO.setDifficultyLevel(question.getDifficultyLevel());
                    qDTO.setHasAnswer(question.getHasAnswer());
                    return qDTO;
                })
                .toList();
        dto.setQuestions(questionDTOs);
        return dto;
    }
}
