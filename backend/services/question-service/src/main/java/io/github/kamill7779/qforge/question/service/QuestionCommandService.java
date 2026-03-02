package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.question.dto.CreateQuestionRequest;
import io.github.kamill7779.qforge.question.dto.CreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.QuestionOverviewResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskSubmitRequest;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import io.github.kamill7779.qforge.question.dto.UpdateDifficultyRequest;
import io.github.kamill7779.qforge.question.dto.UpdateStemRequest;
import io.github.kamill7779.qforge.question.dto.UpdateTagsRequest;
import java.util.List;

public interface QuestionCommandService {

    QuestionStatusResponse createDraft(CreateQuestionRequest request, String requestUser);

    /** Validates stem XML then persists it. */
    QuestionStatusResponse updateStem(String questionUuid, UpdateStemRequest request, String requestUser);

    QuestionStatusResponse addAnswer(String questionUuid, CreateAnswerRequest request, String requestUser);

    /** Submits an OCR task; bizType must be QUESTION_STEM or ANSWER_CONTENT. */
    OcrTaskAcceptedResponse submitOcrTask(String questionUuid, OcrTaskSubmitRequest request, String requestUser);

    QuestionStatusResponse completeQuestion(String questionUuid, String requestUser);

    void deleteDraftQuestion(String questionUuid, String requestUser);

    List<QuestionOverviewResponse> listUserQuestions(String requestUser);

    /** Replaces all secondary tags for a question. */
    QuestionStatusResponse updateTags(String questionUuid, UpdateTagsRequest request, String requestUser);

    /** Updates the difficulty P-value for a question. */
    QuestionStatusResponse updateDifficulty(String questionUuid, UpdateDifficultyRequest request, String requestUser);

    /** Triggers async AI analysis for tags + difficulty. */
    void requestAiAnalysis(String questionUuid, String requestUser);
}
