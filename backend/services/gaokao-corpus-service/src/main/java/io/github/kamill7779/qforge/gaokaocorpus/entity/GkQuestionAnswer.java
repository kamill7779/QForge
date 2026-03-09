package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("gk_question_answer")
public class GkQuestionAnswer {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("answer_uuid")
    private String answerUuid;

    @TableField("question_id")
    private Long questionId;

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

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAnswerUuid() { return answerUuid; }
    public void setAnswerUuid(String answerUuid) { this.answerUuid = answerUuid; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

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
}
