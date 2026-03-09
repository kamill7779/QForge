package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_rag_chunk")
public class GkRagChunk {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("chunk_uuid")
    private String chunkUuid;

    @TableField("question_id")
    private Long questionId;

    @TableField("chunk_type")
    private String chunkType;

    @TableField("chunk_text")
    private String chunkText;

    @TableField("token_count")
    private Integer tokenCount;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChunkUuid() { return chunkUuid; }
    public void setChunkUuid(String chunkUuid) { this.chunkUuid = chunkUuid; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getChunkType() { return chunkType; }
    public void setChunkType(String chunkType) { this.chunkType = chunkType; }

    public String getChunkText() { return chunkText; }
    public void setChunkText(String chunkText) { this.chunkText = chunkText; }

    public Integer getTokenCount() { return tokenCount; }
    public void setTokenCount(Integer tokenCount) { this.tokenCount = tokenCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
