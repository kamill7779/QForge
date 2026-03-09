package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_draft_answer")
public class GkDraftAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("draft_answer_uuid")
    private String draftAnswerUuid;

    @TableField("draft_question_id")
    private Long draftQuestionId;

    @TableField("answer_type")
    private String answerType;

    @TableField("answer_text")
    private String answerText;

    @TableField("answer_xml")
    private String answerXml;

    @TableField("is_official")
    private Boolean isOfficial;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDraftAnswerUuid() { return draftAnswerUuid; }
    public void setDraftAnswerUuid(String draftAnswerUuid) { this.draftAnswerUuid = draftAnswerUuid; }

    public Long getDraftQuestionId() { return draftQuestionId; }
    public void setDraftQuestionId(Long draftQuestionId) { this.draftQuestionId = draftQuestionId; }

    public String getAnswerType() { return answerType; }
    public void setAnswerType(String answerType) { this.answerType = answerType; }

    public String getAnswerText() { return answerText; }
    public void setAnswerText(String answerText) { this.answerText = answerText; }

    public String getAnswerXml() { return answerXml; }
    public void setAnswerXml(String answerXml) { this.answerXml = answerXml; }

    public Boolean getIsOfficial() { return isOfficial; }
    public void setIsOfficial(Boolean isOfficial) { this.isOfficial = isOfficial; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
