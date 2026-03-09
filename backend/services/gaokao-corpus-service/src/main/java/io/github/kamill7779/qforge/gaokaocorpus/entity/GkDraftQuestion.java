package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("gk_draft_question")
public class GkDraftQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("draft_question_uuid")
    private String draftQuestionUuid;

    @TableField("draft_paper_id")
    private Long draftPaperId;

    @TableField("draft_section_id")
    private Long draftSectionId;

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

    @TableField("has_answer")
    private Boolean hasAnswer;

    @TableField("edit_version")
    private Integer editVersion;

    @TableField("created_at")
    private LocalDateTime createdAt;

    @TableField("updated_at")
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDraftQuestionUuid() { return draftQuestionUuid; }
    public void setDraftQuestionUuid(String draftQuestionUuid) { this.draftQuestionUuid = draftQuestionUuid; }

    public Long getDraftPaperId() { return draftPaperId; }
    public void setDraftPaperId(Long draftPaperId) { this.draftPaperId = draftPaperId; }

    public Long getDraftSectionId() { return draftSectionId; }
    public void setDraftSectionId(Long draftSectionId) { this.draftSectionId = draftSectionId; }

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

    public Boolean getHasAnswer() { return hasAnswer; }
    public void setHasAnswer(Boolean hasAnswer) { this.hasAnswer = hasAnswer; }

    public Integer getEditVersion() { return editVersion; }
    public void setEditVersion(Integer editVersion) { this.editVersion = editVersion; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
