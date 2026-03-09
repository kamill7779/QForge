package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.math.BigDecimal;

public class UpdateDraftPaperRequest {

    private String paperName;
    private String paperTypeCode;
    private Short examYear;
    private String provinceCode;
    private BigDecimal totalScore;
    private Integer durationMinutes;

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
}
