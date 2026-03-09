package io.github.kamill7779.qforge.gaokaoanalysis.dto;

import java.util.List;

public class RecommendGroupDTO {

    private String relationType;
    private List<RecommendedQuestionDTO> questions;

    public String getRelationType() {
        return relationType;
    }

    public void setRelationType(String relationType) {
        this.relationType = relationType;
    }

    public List<RecommendedQuestionDTO> getQuestions() {
        return questions;
    }

    public void setQuestions(List<RecommendedQuestionDTO> questions) {
        this.questions = questions;
    }
}
