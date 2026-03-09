package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("gk_question_option")
public class GkQuestionOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("option_uuid")
    private String optionUuid;

    @TableField("question_id")
    private Long questionId;

    @TableField("option_label")
    private String optionLabel;

    @TableField("option_text")
    private String optionText;

    @TableField("option_xml")
    private String optionXml;

    @TableField("is_correct")
    private Boolean isCorrect;

    @TableField("sort_order")
    private Integer sortOrder;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getOptionUuid() { return optionUuid; }
    public void setOptionUuid(String optionUuid) { this.optionUuid = optionUuid; }

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }

    public String getOptionLabel() { return optionLabel; }
    public void setOptionLabel(String optionLabel) { this.optionLabel = optionLabel; }

    public String getOptionText() { return optionText; }
    public void setOptionText(String optionText) { this.optionText = optionText; }

    public String getOptionXml() { return optionXml; }
    public void setOptionXml(String optionXml) { this.optionXml = optionXml; }

    public Boolean getIsCorrect() { return isCorrect; }
    public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
