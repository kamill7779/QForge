package io.github.kamill7779.qforge.gaokaocorpus.dto;

import java.time.LocalDateTime;
import java.util.List;

public class IngestSessionDTO {

    private String sessionUuid;
    private String status;
    private String sourceKind;
    private String subjectCode;
    private String operatorUser;
    private String paperNameGuess;
    private Short examYearGuess;
    private String provinceCodeGuess;
    private String errorMsg;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private List<String> sourceFileUuids;

    public String getSessionUuid() { return sessionUuid; }
    public void setSessionUuid(String sessionUuid) { this.sessionUuid = sessionUuid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSourceKind() { return sourceKind; }
    public void setSourceKind(String sourceKind) { this.sourceKind = sourceKind; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getOperatorUser() { return operatorUser; }
    public void setOperatorUser(String operatorUser) { this.operatorUser = operatorUser; }

    public String getPaperNameGuess() { return paperNameGuess; }
    public void setPaperNameGuess(String paperNameGuess) { this.paperNameGuess = paperNameGuess; }

    public Short getExamYearGuess() { return examYearGuess; }
    public void setExamYearGuess(Short examYearGuess) { this.examYearGuess = examYearGuess; }

    public String getProvinceCodeGuess() { return provinceCodeGuess; }
    public void setProvinceCodeGuess(String provinceCodeGuess) { this.provinceCodeGuess = provinceCodeGuess; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public List<String> getSourceFileUuids() { return sourceFileUuids; }
    public void setSourceFileUuids(List<String> sourceFileUuids) { this.sourceFileUuids = sourceFileUuids; }
}
