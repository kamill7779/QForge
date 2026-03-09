package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gk_draft_paper")
public class GkDraftPaper {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("draft_paper_uuid")
    private String draftPaperUuid;

    @TableField("session_id")
    private Long sessionId;

    @TableField("paper_name")
    private String paperName;

    @TableField("paper_type_code")
    private String paperTypeCode;

    @TableField("exam_year")
    private Short examYear;

    @TableField("province_code")
    private String provinceCode;

    @TableField("total_score")
    private BigDecimal totalScore;

    @TableField("duration_minutes")
    private Integer durationMinutes;

    private String status;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDraftPaperUuid() { return draftPaperUuid; }
    public void setDraftPaperUuid(String draftPaperUuid) { this.draftPaperUuid = draftPaperUuid; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
