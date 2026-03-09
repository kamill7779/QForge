package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gk_recommend_edge")
public class GkRecommendEdge {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("source_question_id")
    private Long sourceQuestionId;

    @TableField("target_question_id")
    private Long targetQuestionId;

    @TableField("relation_type")
    private String relationType;

    private BigDecimal score;

    @TableField("computed_at")
    private LocalDateTime computedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSourceQuestionId() { return sourceQuestionId; }
    public void setSourceQuestionId(Long sourceQuestionId) { this.sourceQuestionId = sourceQuestionId; }

    public Long getTargetQuestionId() { return targetQuestionId; }
    public void setTargetQuestionId(Long targetQuestionId) { this.targetQuestionId = targetQuestionId; }

    public String getRelationType() { return relationType; }
    public void setRelationType(String relationType) { this.relationType = relationType; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public LocalDateTime getComputedAt() { return computedAt; }
    public void setComputedAt(LocalDateTime computedAt) { this.computedAt = computedAt; }
}
