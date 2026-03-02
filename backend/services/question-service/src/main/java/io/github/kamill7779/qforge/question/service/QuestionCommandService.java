package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.question.dto.BatchCreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.CreateQuestionRequest;
import io.github.kamill7779.qforge.question.dto.CreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.QuestionOverviewResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskSubmitRequest;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import io.github.kamill7779.qforge.question.dto.UpdateStemRequest;
import java.util.List;

public interface QuestionCommandService {

    QuestionStatusResponse createDraft(CreateQuestionRequest request, String requestUser);

    /** 更新题干 XML 文本（服务端强制 XML 校验）。 */
    QuestionStatusResponse updateStem(String questionUuid, UpdateStemRequest request, String requestUser);

    QuestionStatusResponse addAnswer(String questionUuid, CreateAnswerRequest request, String requestUser);

    /** 批量添加答案（兼容 OCR 识别结果 + 手动输入）。 */
    QuestionStatusResponse batchAddAnswers(String questionUuid, BatchCreateAnswerRequest request, String requestUser);

    /** 提交 OCR 任务，bizType 由请求体指定（QUESTION_STEM / ANSWER_CONTENT）。 */
    OcrTaskAcceptedResponse submitOcrTask(String questionUuid, OcrTaskSubmitRequest request, String requestUser);

    QuestionStatusResponse completeQuestion(String questionUuid, String requestUser);

    void deleteDraftQuestion(String questionUuid, String requestUser);

    List<QuestionOverviewResponse> listUserQuestions(String requestUser);
}
