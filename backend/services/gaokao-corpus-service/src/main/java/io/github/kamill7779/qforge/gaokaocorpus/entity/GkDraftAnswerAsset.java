package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_draft_answer_asset")
public class GkDraftAnswerAsset {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("draft_answer_id")
    private Long draftAnswerId;

    @TableField("asset_type")
    private String assetType;

    @TableField("ref_key")
    private String refKey;

    @TableField("storage_ref")
    private String storageRef;

    @TableField("sort_order")
    private Integer sortOrder;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getDraftAnswerId() { return draftAnswerId; }
    public void setDraftAnswerId(Long draftAnswerId) { this.draftAnswerId = draftAnswerId; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getRefKey() { return refKey; }
    public void setRefKey(String refKey) { this.refKey = refKey; }

    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
