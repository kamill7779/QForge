package io.github.kamill7779.qforge.gaokaocorpus.client;

import java.util.List;

public class AnalyzePaperRequest {

    private String draftPaperUuid;
    private List<Long> draftQuestionIds;

    public String getDraftPaperUuid() { return draftPaperUuid; }
    public void setDraftPaperUuid(String draftPaperUuid) { this.draftPaperUuid = draftPaperUuid; }

    public List<Long> getDraftQuestionIds() { return draftQuestionIds; }
    public void setDraftQuestionIds(List<Long> draftQuestionIds) { this.draftQuestionIds = draftQuestionIds; }
}
