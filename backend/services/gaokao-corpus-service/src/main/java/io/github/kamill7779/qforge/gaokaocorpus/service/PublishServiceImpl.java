package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkQuestionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftAnswer;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftOption;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftPaper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftProfilePreview;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkDraftSection;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSession;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkPaper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkPaperSection;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionAnswer;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionOption;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionProfile;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftAnswerMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftOptionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftPaperMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftProfilePreviewMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkDraftSectionMapper;
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
    private final GkDraftProfilePreviewMapper draftProfilePreviewMapper;
    private final GkIngestSessionMapper ingestSessionMapper;
    private final GkPaperMapper paperMapper;
    private final GkPaperSectionMapper paperSectionMapper;
    private final GkQuestionMapper questionMapper;
    private final GkQuestionOptionMapper questionOptionMapper;
    private final GkQuestionAnswerMapper questionAnswerMapper;
    private final GkQuestionProfileMapper questionProfileMapper;

    public PublishServiceImpl(
            GkDraftPaperMapper draftPaperMapper,
            GkDraftSectionMapper draftSectionMapper,
            GkDraftQuestionMapper draftQuestionMapper,
            GkDraftOptionMapper draftOptionMapper,
            GkDraftAnswerMapper draftAnswerMapper,
            GkDraftProfilePreviewMapper draftProfilePreviewMapper,
            GkIngestSessionMapper ingestSessionMapper,
            GkPaperMapper paperMapper,
            GkPaperSectionMapper paperSectionMapper,
            GkQuestionMapper questionMapper,
            GkQuestionOptionMapper questionOptionMapper,
            GkQuestionAnswerMapper questionAnswerMapper,
            GkQuestionProfileMapper questionProfileMapper
    ) {
        this.draftPaperMapper = draftPaperMapper;
        this.draftSectionMapper = draftSectionMapper;
        this.draftQuestionMapper = draftQuestionMapper;
        this.draftOptionMapper = draftOptionMapper;
        this.draftAnswerMapper = draftAnswerMapper;
        this.draftProfilePreviewMapper = draftProfilePreviewMapper;
        this.ingestSessionMapper = ingestSessionMapper;
        this.paperMapper = paperMapper;
        this.paperSectionMapper = paperSectionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionAnswerMapper = questionAnswerMapper;
        this.questionProfileMapper = questionProfileMapper;
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

        // Get session UUID
        GkIngestSession session = ingestSessionMapper.selectById(draftPaper.getSessionId());
        String sessionUuid = session != null ? session.getSessionUuid() : null;

        // 1. Create published paper
        GkPaper paper = new GkPaper();
        paper.setPaperUuid(UUID.randomUUID().toString());
        paper.setSourceSessionUuid(sessionUuid);
        paper.setPaperName(draftPaper.getPaperName());
        paper.setPaperTypeCode(draftPaper.getPaperTypeCode());
        paper.setExamYear(draftPaper.getExamYear());
        paper.setProvinceCode(draftPaper.getProvinceCode());
        paper.setSubjectCode("MATH");
        paper.setStatus("READY");
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

            // 5. Copy answers
            List<GkDraftAnswer> answers = draftAnswerMapper.selectList(
                    new LambdaQueryWrapper<GkDraftAnswer>()
                            .eq(GkDraftAnswer::getDraftQuestionId, dq.getId())
                            .orderByAsc(GkDraftAnswer::getSortOrder));
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

        // 7. Update session and draft paper status
        draftPaper.setStatus("READY_TO_PUBLISH");
        draftPaper.setUpdatedAt(LocalDateTime.now());
        draftPaperMapper.updateById(draftPaper);
        if (session != null) {
            session.setStatus("PUBLISHED");
            session.setUpdatedAt(LocalDateTime.now());
            ingestSessionMapper.updateById(session);
        }

        log.info("Published paper complete: uuid={}, questions={}", paper.getPaperUuid(), questionDTOs.size());

        GkPaperDTO dto = new GkPaperDTO();
        dto.setPaperUuid(paper.getPaperUuid());
        dto.setPaperName(paper.getPaperName());
        dto.setPaperTypeCode(paper.getPaperTypeCode());
        dto.setExamYear(paper.getExamYear());
        dto.setProvinceCode(paper.getProvinceCode());
        dto.setSubjectCode(paper.getSubjectCode());
        dto.setStatus(paper.getStatus());
        dto.setQuestions(questionDTOs);
        return dto;
    }
}
