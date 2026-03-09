package io.github.kamill7779.qforge.internal.api;

/**
 * 从高考数学语料库物化到 question-core-service 正式题库的响应。
 */
public class CreateQuestionFromGaokaoResponse {

    private String questionUuid;
    private Long questionId;
    private boolean success;
    private String errorMessage;

    public String getQuestionUuid() { return questionUuid; }
    public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
}
