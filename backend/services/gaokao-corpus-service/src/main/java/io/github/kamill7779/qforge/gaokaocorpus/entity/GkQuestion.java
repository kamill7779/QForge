package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gk_question")
public class GkQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("question_uuid")
    private String questionUuid;

    @TableField("paper_id")
    private Long paperId;

    @TableField("section_id")
    private Long sectionId;

    @TableField("parent_question_id")
    private Long parentQuestionId;

    @TableField("root_question_id")
    private Long rootQuestionId;

    @TableField("question_no")
    private String questionNo;

    @TableField("question_type_code")
    private String questionTypeCode;

    @TableField("answer_mode")
    private String answerMode;

    @TableField("stem_text")
    private String stemText;

    @TableField("stem_xml")
    private String stemXml;

    @TableField("normalized_stem_text")
    private String normalizedStemText;

    private BigDecimal score;

    @TableField("difficulty_score")
    private BigDecimal difficultyScore;

    @TableField("difficulty_level")
    private String difficultyLevel;

    @TableField("reasoning_step_count")
    private Integer reasoningStepCount;

    @TableField("has_answer")
    private Boolean hasAnswer;

    @TableField("published_at")
    private LocalDateTime publishedAt;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getQuestionUuid() { return questionUuid; }
    public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

    public Long getPaperId() { return paperId; }
    public void setPaperId(Long paperId) { this.paperId = paperId; }

    public Long getSectionId() { return sectionId; }
    public void setSectionId(Long sectionId) { this.sectionId = sectionId; }

    public Long getParentQuestionId() { return parentQuestionId; }
    public void setParentQuestionId(Long parentQuestionId) { this.parentQuestionId = parentQuestionId; }

    public Long getRootQuestionId() { return rootQuestionId; }
    public void setRootQuestionId(Long rootQuestionId) { this.rootQuestionId = rootQuestionId; }

    public String getQuestionNo() { return questionNo; }
    public void setQuestionNo(String questionNo) { this.questionNo = questionNo; }

    public String getQuestionTypeCode() { return questionTypeCode; }
    public void setQuestionTypeCode(String questionTypeCode) { this.questionTypeCode = questionTypeCode; }

    public String getAnswerMode() { return answerMode; }
    public void setAnswerMode(String answerMode) { this.answerMode = answerMode; }

    public String getStemText() { return stemText; }
    public void setStemText(String stemText) { this.stemText = stemText; }

    public String getStemXml() { return stemXml; }
    public void setStemXml(String stemXml) { this.stemXml = stemXml; }

    public String getNormalizedStemText() { return normalizedStemText; }
    public void setNormalizedStemText(String normalizedStemText) { this.normalizedStemText = normalizedStemText; }

    public BigDecimal getScore() { return score; }
    public void setScore(BigDecimal score) { this.score = score; }

    public BigDecimal getDifficultyScore() { return difficultyScore; }
    public void setDifficultyScore(BigDecimal difficultyScore) { this.difficultyScore = difficultyScore; }

    public String getDifficultyLevel() { return difficultyLevel; }
    public void setDifficultyLevel(String difficultyLevel) { this.difficultyLevel = difficultyLevel; }

    public Integer getReasoningStepCount() { return reasoningStepCount; }
    public void setReasoningStepCount(Integer reasoningStepCount) { this.reasoningStepCount = reasoningStepCount; }

    public Boolean getHasAnswer() { return hasAnswer; }
    public void setHasAnswer(Boolean hasAnswer) { this.hasAnswer = hasAnswer; }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void setPublishedAt(LocalDateTime publishedAt) { this.publishedAt = publishedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
