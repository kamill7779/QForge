package io.github.kamill7779.qforge.gaokaoanalysis.service;

import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalysisResultDTO;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalyzePaperRequest;
import io.github.kamill7779.qforge.gaokaoanalysis.dto.AnalyzeQuestionRequest;
import java.util.List;

public interface AiAnalysisService {

    /**
     * Run AI analysis on a single question draft.
     */
    AnalysisResultDTO analyzeQuestion(AnalyzeQuestionRequest request);

    /**
     * Run AI analysis on all questions in a paper draft.
     */
    List<AnalysisResultDTO> analyzePaper(AnalyzePaperRequest request);
}
