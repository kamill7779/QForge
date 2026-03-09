package io.github.kamill7779.qforge.gaokaocorpus.client;

public class AnalyzePaperRequest {

    private String draftPaperUuid;
    private String sessionUuid;

    public String getDraftPaperUuid() { return draftPaperUuid; }
    public void setDraftPaperUuid(String draftPaperUuid) { this.draftPaperUuid = draftPaperUuid; }

    public String getSessionUuid() { return sessionUuid; }
    public void setSessionUuid(String sessionUuid) { this.sessionUuid = sessionUuid; }
}
