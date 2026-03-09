package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;
import java.util.List;

public class DraftPaperDTO {

    private String draftPaperUuid;
    private String paperName;
    private String paperTypeCode;
    private Short examYear;
    private String provinceCode;
    private BigDecimal totalScore;
    private Integer durationMinutes;
    private String status;
    private List<DraftSectionDTO> sections;

    public String getDraftPaperUuid() { return draftPaperUuid; }
    public void setDraftPaperUuid(String draftPaperUuid) { this.draftPaperUuid = draftPaperUuid; }

    public String getPaperName() { return paperName; }
    public void setPaperName(String paperName) { this.paperName = paperName; }

    public String getPaperTypeCode() { return paperTypeCode; }
    public void setPaperTypeCode(String paperTypeCode) { this.paperTypeCode = paperTypeCode; }

    public Short getExamYear() { return examYear; }
    public void setExamYear(Short examYear) { this.examYear = examYear; }

    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }

    public BigDecimal getTotalScore() { return totalScore; }
    public void setTotalScore(BigDecimal totalScore) { this.totalScore = totalScore; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<DraftSectionDTO> getSections() { return sections; }
    public void setSections(List<DraftSectionDTO> sections) { this.sections = sections; }

    public static class DraftSectionDTO {
        private String draftSectionUuid;
        private String sectionCode;
        private String sectionTitle;
        private Integer sortOrder;
        private List<DraftQuestionDTO> questions;

        public String getDraftSectionUuid() { return draftSectionUuid; }
        public void setDraftSectionUuid(String draftSectionUuid) { this.draftSectionUuid = draftSectionUuid; }

        public String getSectionCode() { return sectionCode; }
        public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }

        public String getSectionTitle() { return sectionTitle; }
        public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }

        public Integer getSortOrder() { return sortOrder; }
        public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

        public List<DraftQuestionDTO> getQuestions() { return questions; }
        public void setQuestions(List<DraftQuestionDTO> questions) { this.questions = questions; }
    }
}
