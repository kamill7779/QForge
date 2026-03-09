package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_draft_section")
public class GkDraftSection {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("draft_section_uuid")
    private String draftSectionUuid;

    @TableField("draft_paper_id")
    private Long draftPaperId;

    @TableField("section_code")
    private String sectionCode;

    @TableField("section_title")
    private String sectionTitle;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDraftSectionUuid() { return draftSectionUuid; }
    public void setDraftSectionUuid(String draftSectionUuid) { this.draftSectionUuid = draftSectionUuid; }

    public Long getDraftPaperId() { return draftPaperId; }
    public void setDraftPaperId(Long draftPaperId) { this.draftPaperId = draftPaperId; }

    public String getSectionCode() { return sectionCode; }
    public void setSectionCode(String sectionCode) { this.sectionCode = sectionCode; }

    public String getSectionTitle() { return sectionTitle; }
    public void setSectionTitle(String sectionTitle) { this.sectionTitle = sectionTitle; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
