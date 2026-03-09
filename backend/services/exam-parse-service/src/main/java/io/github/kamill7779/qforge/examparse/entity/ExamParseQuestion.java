package io.github.kamill7779.qforge.examparse.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("q_exam_parse_question")
public class ExamParseQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_uuid")
    private String taskUuid;

    @TableField("seq_no")
    private Integer seqNo;

    @TableField("question_type")
    private String questionType;

    @TableField("raw_stem_text")
    private String rawStemText;

    @TableField("stem_xml")
    private String stemXml;

    @TableField("raw_answer_text")
    private String rawAnswerText;

    @TableField("answer_xml")
    private String answerXml;

    @TableField("stem_images_json")
    private String stemImagesJson;

    @TableField("answer_images_json")
    private String answerImagesJson;

    @TableField("source_pages")
    private String sourcePages;

    @TableField("parse_error")
    private Boolean parseError;

    @TableField("question_uuid")
    private String questionUuid;

    @TableField("main_tags_json")
    private String mainTagsJson;

    @TableField("secondary_tags_json")
    private String secondaryTagsJson;

    private BigDecimal difficulty;

    @TableField("confirm_status")
    private String confirmStatus;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;

    // --- getters & setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskUuid() { return taskUuid; }
    public void setTaskUuid(String taskUuid) { this.taskUuid = taskUuid; }

    public Integer getSeqNo() { return seqNo; }
    public void setSeqNo(Integer seqNo) { this.seqNo = seqNo; }

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }

    public String getRawStemText() { return rawStemText; }
    public void setRawStemText(String rawStemText) { this.rawStemText = rawStemText; }

    public String getStemXml() { return stemXml; }
    public void setStemXml(String stemXml) { this.stemXml = stemXml; }

    public String getRawAnswerText() { return rawAnswerText; }
    public void setRawAnswerText(String rawAnswerText) { this.rawAnswerText = rawAnswerText; }

    public String getAnswerXml() { return answerXml; }
    public void setAnswerXml(String answerXml) { this.answerXml = answerXml; }

    public String getStemImagesJson() { return stemImagesJson; }
    public void setStemImagesJson(String stemImagesJson) { this.stemImagesJson = stemImagesJson; }

    public String getAnswerImagesJson() { return answerImagesJson; }
    public void setAnswerImagesJson(String answerImagesJson) { this.answerImagesJson = answerImagesJson; }

    public String getSourcePages() { return sourcePages; }
    public void setSourcePages(String sourcePages) { this.sourcePages = sourcePages; }

    public Boolean getParseError() { return parseError; }
    public void setParseError(Boolean parseError) { this.parseError = parseError; }

    public String getQuestionUuid() { return questionUuid; }
    public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

    public String getMainTagsJson() { return mainTagsJson; }
    public void setMainTagsJson(String mainTagsJson) { this.mainTagsJson = mainTagsJson; }

    public String getSecondaryTagsJson() { return secondaryTagsJson; }
    public void setSecondaryTagsJson(String secondaryTagsJson) { this.secondaryTagsJson = secondaryTagsJson; }

    public BigDecimal getDifficulty() { return difficulty; }
    public void setDifficulty(BigDecimal difficulty) { this.difficulty = difficulty; }

    public String getConfirmStatus() { return confirmStatus; }
    public void setConfirmStatus(String confirmStatus) { this.confirmStatus = confirmStatus; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
