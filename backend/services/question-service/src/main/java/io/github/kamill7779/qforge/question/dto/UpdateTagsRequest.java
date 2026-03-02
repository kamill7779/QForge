package io.github.kamill7779.qforge.question.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public class UpdateTagsRequest {

    @NotNull
    private List<String> tags;

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }
}
