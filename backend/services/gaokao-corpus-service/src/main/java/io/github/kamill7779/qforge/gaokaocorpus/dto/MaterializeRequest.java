package io.github.kamill7779.qforge.gaokaocorpus.dto;

public class MaterializeRequest {

    private Long gkQuestionId;
    private String questionUuid;
    private String mode;

    public Long getGkQuestionId() { return gkQuestionId; }
    public void setGkQuestionId(Long gkQuestionId) { this.gkQuestionId = gkQuestionId; }

    public String getQuestionUuid() { return questionUuid; }
    public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
}
