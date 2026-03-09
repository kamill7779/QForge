package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_question_materialization")
public class GkQuestionMaterialization {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("gk_question_id")
    private Long gkQuestionId;

    @TableField("target_question_uuid")
    private String targetQuestionUuid;

    @TableField("owner_user")
    private String ownerUser;

    private String mode;

    private String status;

    @TableField("source_hash")
    private String sourceHash;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getGkQuestionId() { return gkQuestionId; }
    public void setGkQuestionId(Long gkQuestionId) { this.gkQuestionId = gkQuestionId; }

    public String getTargetQuestionUuid() { return targetQuestionUuid; }
    public void setTargetQuestionUuid(String targetQuestionUuid) { this.targetQuestionUuid = targetQuestionUuid; }

    public String getOwnerUser() { return ownerUser; }
    public void setOwnerUser(String ownerUser) { this.ownerUser = ownerUser; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getSourceHash() { return sourceHash; }
    public void setSourceHash(String sourceHash) { this.sourceHash = sourceHash; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
