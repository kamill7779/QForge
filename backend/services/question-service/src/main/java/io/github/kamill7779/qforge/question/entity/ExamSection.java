package io.github.kamill7779.qforge.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("q_exam_section")
public class ExamSection {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("section_uuid")
    private String sectionUuid;

    @TableField("paper_id")
    private Long paperId;

    private String title;
    private String description;

    @TableField("question_type_code")
    private String questionTypeCode;

    @TableField("default_score")
    private BigDecimal defaultScore;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    // ── getters / setters ──

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getSectionUuid() { return sectionUuid; }
    public void setSectionUuid(String sectionUuid) { this.sectionUuid = sectionUuid; }

    public Long getPaperId() { return paperId; }
    public void setPaperId(Long paperId) { this.paperId = paperId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getQuestionTypeCode() { return questionTypeCode; }
    public void setQuestionTypeCode(String questionTypeCode) { this.questionTypeCode = questionTypeCode; }

    public BigDecimal getDefaultScore() { return defaultScore; }
    public void setDefaultScore(BigDecimal defaultScore) { this.defaultScore = defaultScore; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
