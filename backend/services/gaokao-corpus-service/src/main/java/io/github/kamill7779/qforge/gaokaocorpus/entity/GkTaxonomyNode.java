package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_taxonomy_node")
public class GkTaxonomyNode {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("node_uuid")
    private String nodeUuid;

    @TableField("taxonomy_code")
    private String taxonomyCode;

    @TableField("parent_node_id")
    private Long parentNodeId;

    @TableField("node_code")
    private String nodeCode;

    @TableField("node_name")
    private String nodeName;

    @TableField("node_path")
    private String nodePath;

    @TableField("sort_order")
    private Integer sortOrder;

    private Boolean enabled;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNodeUuid() { return nodeUuid; }
    public void setNodeUuid(String nodeUuid) { this.nodeUuid = nodeUuid; }

    public String getTaxonomyCode() { return taxonomyCode; }
    public void setTaxonomyCode(String taxonomyCode) { this.taxonomyCode = taxonomyCode; }

    public Long getParentNodeId() { return parentNodeId; }
    public void setParentNodeId(Long parentNodeId) { this.parentNodeId = parentNodeId; }

    public String getNodeCode() { return nodeCode; }
    public void setNodeCode(String nodeCode) { this.nodeCode = nodeCode; }

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public String getNodePath() { return nodePath; }
    public void setNodePath(String nodePath) { this.nodePath = nodePath; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
