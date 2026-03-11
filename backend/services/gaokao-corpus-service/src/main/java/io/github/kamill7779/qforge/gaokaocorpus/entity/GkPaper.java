package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_paper")
public class GkPaper {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("paper_uuid")
    private String paperUuid;

    @TableField("source_session_uuid")
    private String sourceSessionUuid;

    @TableField("draft_paper_id")
    private Long draftPaperId;

    @TableField("paper_name")
    private String paperName;

    @TableField("paper_type_code")
    private String paperTypeCode;

    @TableField("exam_year")
    private Short examYear;

    @TableField("province_code")
    private String provinceCode;

    @TableField("subject_code")
    private String subjectCode;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPaperUuid() { return paperUuid; }
    public void setPaperUuid(String paperUuid) { this.paperUuid = paperUuid; }

    public String getSourceSessionUuid() { return sourceSessionUuid; }
    public void setSourceSessionUuid(String sourceSessionUuid) { this.sourceSessionUuid = sourceSessionUuid; }

    public Long getDraftPaperId() { return draftPaperId; }
    public void setDraftPaperId(Long draftPaperId) { this.draftPaperId = draftPaperId; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
