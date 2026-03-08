package io.github.kamill7779.qforge.question.dto.exam;

import java.math.BigDecimal;
import java.util.List;

/**
 * 整卷内容保存请求 — 原子替换所有大题 + 题目。
 * PUT /api/exam-papers/{paperUuid}/content
 */
public class SaveExamContentRequest {

    private List<SectionPayload> sections;

    public List<SectionPayload> getSections() { return sections; }
    public void setSections(List<SectionPayload> sections) { this.sections = sections; }

    /**
     * 大题定义。
     */
    public static class SectionPayload {
        private String sectionUuid;
        private String title;
        private String description;
        private String questionTypeCode;
        private BigDecimal defaultScore;
        private List<QuestionPayload> questions;

        public String getSectionUuid() { return sectionUuid; }
        public void setSectionUuid(String sectionUuid) { this.sectionUuid = sectionUuid; }

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getQuestionTypeCode() { return questionTypeCode; }
        public void setQuestionTypeCode(String questionTypeCode) { this.questionTypeCode = questionTypeCode; }

        public BigDecimal getDefaultScore() { return defaultScore; }
        public void setDefaultScore(BigDecimal defaultScore) { this.defaultScore = defaultScore; }

        public List<QuestionPayload> getQuestions() { return questions; }
        public void setQuestions(List<QuestionPayload> questions) { this.questions = questions; }
    }

    /**
     * 大题内的题目。
     */
    public static class QuestionPayload {
        private String questionUuid;
        private BigDecimal score;
        private String note;

        public String getQuestionUuid() { return questionUuid; }
        public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

        public BigDecimal getScore() { return score; }
        public void setScore(BigDecimal score) { this.score = score; }

        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }
}
