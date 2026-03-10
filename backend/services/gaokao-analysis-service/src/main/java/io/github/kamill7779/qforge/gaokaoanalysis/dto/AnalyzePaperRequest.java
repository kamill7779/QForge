package io.github.kamill7779.qforge.gaokaoanalysis.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public class AnalyzePaperRequest {

    @NotBlank
    private String draftPaperUuid;

    private List<Long> draftQuestionIds;
    private List<QuestionPayload> questions;

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

    public List<QuestionPayload> getQuestions() {
        return questions;
    }

    public void setQuestions(List<QuestionPayload> questions) {
        this.questions = questions;
    }

    public static class QuestionPayload {
        private Long draftQuestionId;
        private String stemText;
        private String stemXml;
        private String answerText;
        private String questionTypeCode;

        public Long getDraftQuestionId() {
            return draftQuestionId;
        }

        public void setDraftQuestionId(Long draftQuestionId) {
            this.draftQuestionId = draftQuestionId;
        }

        public String getStemText() {
            return stemText;
        }

        public void setStemText(String stemText) {
            this.stemText = stemText;
        }

        public String getStemXml() {
            return stemXml;
        }

        public void setStemXml(String stemXml) {
            this.stemXml = stemXml;
        }

        public String getAnswerText() {
            return answerText;
        }

        public void setAnswerText(String answerText) {
            this.answerText = answerText;
        }

        public String getQuestionTypeCode() {
            return questionTypeCode;
        }

        public void setQuestionTypeCode(String questionTypeCode) {
            this.questionTypeCode = questionTypeCode;
        }
    }
}
