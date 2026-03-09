package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_ingest_session")
public class GkIngestSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_uuid")
    private String sessionUuid;

    private String status;

    @TableField("source_kind")
    private String sourceKind;

    @TableField("subject_code")
    private String subjectCode;

    @TableField("operator_user")
    private String operatorUser;

    @TableField("paper_name_guess")
    private String paperNameGuess;

    @TableField("exam_year_guess")
    private Short examYearGuess;

    @TableField("province_code_guess")
    private String provinceCodeGuess;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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
}
