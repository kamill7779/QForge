package io.github.kamill7779.qforge.gaokaocorpus.client;

public class AnalyzeQuestionRequest {

    private String draftQuestionUuid;
    private String sessionUuid;

    public String getDraftQuestionUuid() { return draftQuestionUuid; }
    public void setDraftQuestionUuid(String draftQuestionUuid) { this.draftQuestionUuid = draftQuestionUuid; }

    public String getSessionUuid() { return sessionUuid; }
    public void setSessionUuid(String sessionUuid) { this.sessionUuid = sessionUuid; }
}
