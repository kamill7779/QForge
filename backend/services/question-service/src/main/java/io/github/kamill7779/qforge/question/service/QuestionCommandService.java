package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.question.dto.CreateQuestionRequest;
import io.github.kamill7779.qforge.question.dto.CreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.OcrConfirmRequest;
import io.github.kamill7779.qforge.question.dto.QuestionOverviewResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskSubmitRequest;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import java.util.List;

public interface QuestionCommandService {

    QuestionStatusResponse createDraft(CreateQuestionRequest request, String requestUser);

    QuestionStatusResponse addAnswer(String questionUuid, CreateAnswerRequest request, String requestUser);

    OcrTaskAcceptedResponse submitQuestionStemOcr(String questionUuid, OcrTaskSubmitRequest request, String requestUser);

    QuestionStatusResponse confirmOcrTask(String taskUuid, OcrConfirmRequest request, String requestUser);

    QuestionStatusResponse completeQuestion(String questionUuid, String requestUser);

    void deleteDraftQuestion(String questionUuid, String requestUser);

    List<QuestionOverviewResponse> listUserQuestions(String requestUser);
}
