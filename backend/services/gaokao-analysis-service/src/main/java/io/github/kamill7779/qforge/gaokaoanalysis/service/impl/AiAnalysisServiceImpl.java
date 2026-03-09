package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.client.GaokaoCorpusClient;
import io.github.kamill7779.qforge.gaokaoanalysis.config.QForgeAnalysisProperties;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalysisResultDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalyzePaperRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalyzeQuestionRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.service.AiAnalysisService;
import io.github.kamill7779.qforge.gaokaoanalysis.service.TextCleansingService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AiAnalysisServiceImpl implements AiAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AiAnalysisServiceImpl.class);

    private final QForgeAnalysisProperties analysisProperties;
    private final TextCleansingService textCleansingService;
    private final GaokaoCorpusClient corpusClient;

    public AiAnalysisServiceImpl(
            QForgeAnalysisProperties analysisProperties,
            TextCleansingService textCleansingService,
            GaokaoCorpusClient corpusClient
    ) {
        this.analysisProperties = analysisProperties;
        this.textCleansingService = textCleansingService;
        this.corpusClient = corpusClient;
    }

    @Override
    public AnalysisResultDTO analyzeQuestion(AnalyzeQuestionRequest request) {
        log.info("Analyzing question draftQuestionId={} with model={}", request.getDraftQuestionId(), analysisProperties.getAiModel());

        String stemText = request.getStemText() != null ? request.getStemText() : "";
        String cleaned = textCleansingService.cleanStemText(stemText);
        String stemXml = textCleansingService.convertToXml(cleaned);
        String normalized = textCleansingService.normalizeForSearch(cleaned);

        AnalysisResultDTO result = new AnalysisResultDTO();
        result.setKnowledgeTagsJson("[\"FUNCTION\"]");
        result.setMethodTagsJson("[\"SUBSTITUTION\"]");
        result.setFormulaTagsJson("[]");
        result.setMistakeTagsJson("[]");
        result.setAbilityTagsJson("[\"COMPUTATION\",\"LOGIC\"]");
        result.setDifficultyScore(new BigDecimal("0.50"));
        result.setDifficultyLevel("MEDIUM");
        result.setReasoningStepsJson("[\"理解题意\",\"建立模型\",\"计算求解\"]");
        result.setAnalysisSummaryText("本题考查基础数学知识，需要运用基本运算和逻辑推理能力。");
        result.setRecommendSeedText(normalized);
        result.setStemXml(stemXml);

        // Callback to corpus-service to persist the profile preview
        try {
            corpusClient.updateDraftProfile(request.getDraftQuestionId(), result);
            log.info("Profile callback sent for draftQuestionId={}", request.getDraftQuestionId());
        } catch (Exception e) {
            log.warn("Failed to callback corpus-service for draftQuestionId={}: {}", request.getDraftQuestionId(), e.getMessage());
        }

        return result;
    }

    @Override
    public List<AnalysisResultDTO> analyzePaper(AnalyzePaperRequest request) {
        log.info("Analyzing paper draftPaperUuid={}, questionCount={}", request.getDraftPaperUuid(),
                request.getDraftQuestionIds() != null ? request.getDraftQuestionIds().size() : 0);

        List<AnalysisResultDTO> results = new ArrayList<>();
        if (request.getDraftQuestionIds() == null) {
            return results;
        }
        for (Long questionId : request.getDraftQuestionIds()) {
            AnalyzeQuestionRequest qReq = new AnalyzeQuestionRequest();
            qReq.setDraftQuestionId(questionId);
            qReq.setStemText("");
            results.add(analyzeQuestion(qReq));
        }
        return results;
    }
}
