package io.github.kamill7779.qforge.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("q_answer")
public class Answer {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("answer_uuid")
    private String answerUuid;

    @TableField("question_id")
    private Long questionId;

    @TableField("answer_type")
    private String answerType;

    @TableField("latex_text")
    private String latexText;

    @TableField("sort_order")
    private int sortOrder;

    @TableField("is_official")
    private boolean official;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAnswerUuid() {
        return answerUuid;
    }

    public void setAnswerUuid(String answerUuid) {
        this.answerUuid = answerUuid;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public void setQuestionId(Long questionId) {
        this.questionId = questionId;
    }

    public String getAnswerType() {
        return answerType;
    }

    public void setAnswerType(String answerType) {
        this.answerType = answerType;
    }

    public String getLatexText() {
        return latexText;
    }

    public void setLatexText(String latexText) {
        this.latexText = latexText;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public boolean isOfficial() {
        return official;
    }

    public void setOfficial(boolean official) {
        this.official = official;
    }
}
