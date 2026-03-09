package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestionMaterialization;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMaterializationMapper;
import io.github.kamill7779.qforge.internal.api.CreateQuestionFromGaokaoRequest;
import io.github.kamill7779.qforge.internal.api.CreateQuestionFromGaokaoResponse;
import io.github.kamill7779.qforge.internal.api.QuestionCoreClient;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MaterializationServiceImpl implements MaterializationService {

    private static final Logger log = LoggerFactory.getLogger(MaterializationServiceImpl.class);

    private final GkQuestionMapper questionMapper;
    private final GkQuestionMaterializationMapper materializationMapper;
    private final QuestionCoreClient questionCoreClient;

    public MaterializationServiceImpl(
            GkQuestionMapper questionMapper,
            GkQuestionMaterializationMapper materializationMapper,
            QuestionCoreClient questionCoreClient
    ) {
        this.questionMapper = questionMapper;
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
}
