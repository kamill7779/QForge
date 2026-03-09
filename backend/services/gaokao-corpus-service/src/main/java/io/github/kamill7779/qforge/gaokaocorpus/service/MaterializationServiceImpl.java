package io.github.kamill7779.qforge.gaokaocorpus.service;

import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkQuestionMaterializationMapper;
import io.github.kamill7779.qforge.internal.api.QuestionCoreClient;
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
    public void materialize(Long gkQuestionId, String ownerUser) {
        // TODO: implement — call question-core-service via Feign to create a formal question, then record bridge
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
