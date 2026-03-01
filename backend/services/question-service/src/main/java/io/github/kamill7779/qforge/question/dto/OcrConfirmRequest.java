package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class OcrConfirmRequest {

    @NotBlank
    private String confirmedText;
    private List<MainTagSelectionRequest> mainTags;
    private String secondaryTagsText;

    public String getConfirmedText() {
        return confirmedText;
    }

    public void setConfirmedText(String confirmedText) {
        this.confirmedText = confirmedText;
    }

    public List<MainTagSelectionRequest> getMainTags() {
        return mainTags;
    }

    public void setMainTags(List<MainTagSelectionRequest> mainTags) {
        this.mainTags = mainTags;
    }

    public String getSecondaryTagsText() {
        return secondaryTagsText;
    }

    public void setSecondaryTagsText(String secondaryTagsText) {
        this.secondaryTagsText = secondaryTagsText;
    }
}
