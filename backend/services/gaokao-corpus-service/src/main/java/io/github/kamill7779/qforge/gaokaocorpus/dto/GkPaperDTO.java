package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.util.List;

public class GkPaperDTO {

    private String paperUuid;
    private String paperName;
    private String paperTypeCode;
    private Short examYear;
    private String provinceCode;
    private String subjectCode;
    private String status;
    private List<GkQuestionDTO> questions;

    public String getPaperUuid() { return paperUuid; }
    public void setPaperUuid(String paperUuid) { this.paperUuid = paperUuid; }

    public String getPaperName() { return paperName; }
    public void setPaperName(String paperName) { this.paperName = paperName; }

    public String getPaperTypeCode() { return paperTypeCode; }
    public void setPaperTypeCode(String paperTypeCode) { this.paperTypeCode = paperTypeCode; }

    public Short getExamYear() { return examYear; }
    public void setExamYear(Short examYear) { this.examYear = examYear; }

    public String getProvinceCode() { return provinceCode; }
    public void setProvinceCode(String provinceCode) { this.provinceCode = provinceCode; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<GkQuestionDTO> getQuestions() { return questions; }
    public void setQuestions(List<GkQuestionDTO> questions) { this.questions = questions; }
}
