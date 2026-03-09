package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_draft_option")
public class GkDraftOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("draft_option_uuid")
    private String draftOptionUuid;

    @TableField("draft_question_id")
    private Long draftQuestionId;

    @TableField("option_label")
    private String optionLabel;

    @TableField("option_text")
    private String optionText;

    @TableField("option_xml")
    private String optionXml;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDraftOptionUuid() { return draftOptionUuid; }
    public void setDraftOptionUuid(String draftOptionUuid) { this.draftOptionUuid = draftOptionUuid; }

    public Long getDraftQuestionId() { return draftQuestionId; }
    public void setDraftQuestionId(Long draftQuestionId) { this.draftQuestionId = draftQuestionId; }

    public String getOptionLabel() { return optionLabel; }
    public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }

    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }

    public String getOptionXml() { return optionXml; }
    public void setOptionXml(String optionXml) { this.optionXml = optionXml; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
