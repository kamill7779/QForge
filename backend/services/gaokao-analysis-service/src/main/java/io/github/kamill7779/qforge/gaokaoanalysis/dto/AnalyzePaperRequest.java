package io.github.kamill7779.qforge.gaokaoanalysis.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class AnalyzePaperRequest {

    @NotBlank
    private String draftPaperUuid;

    @NotEmpty
    private List<Long> draftQuestionIds;

    public String getDraftPaperUuid() {
        return draftPaperUuid;
    }

    public void setDraftPaperUuid(String draftPaperUuid) {
        this.draftPaperUuid = draftPaperUuid;
    }

    public List<Long> getDraftQuestionIds() {
        return draftQuestionIds;
    }

    public void setDraftQuestionIds(List<Long> draftQuestionIds) {
        this.draftQuestionIds = draftQuestionIds;
    }
}
