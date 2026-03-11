package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_ingest_split_question")
public class GkIngestSplitQuestion {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("seq")
    private Integer seq;

    @TableField("question_type_code")
    private String questionTypeCode;

    @TableField("source_pages_json")
    private String sourcePagesJson;

    @TableField("raw_stem_text")
    private String rawStemText;

    @TableField("stem_xml")
    private String stemXml;

    @TableField("raw_answer_text")
    private String rawAnswerText;

    @TableField("answer_xml")
    private String answerXml;

    @TableField("stem_image_refs_json")
    private String stemImageRefsJson;

    @TableField("answer_image_refs_json")
    private String answerImageRefsJson;

    @TableField("stem_images_json")
    private String stemImagesJson;

    @TableField("answer_images_json")
    private String answerImagesJson;

    @TableField("parse_error")
    private Boolean parseError;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }

    public String getQuestionTypeCode() { return questionTypeCode; }
    public void setQuestionTypeCode(String questionTypeCode) { this.questionTypeCode = questionTypeCode; }

    public String getSourcePagesJson() { return sourcePagesJson; }
    public void setSourcePagesJson(String sourcePagesJson) { this.sourcePagesJson = sourcePagesJson; }

    public String getRawStemText() { return rawStemText; }
    public void setRawStemText(String rawStemText) { this.rawStemText = rawStemText; }

    public String getStemXml() { return stemXml; }
    public void setStemXml(String stemXml) { this.stemXml = stemXml; }

    public String getRawAnswerText() { return rawAnswerText; }
    public void setRawAnswerText(String rawAnswerText) { this.rawAnswerText = rawAnswerText; }

    public String getAnswerXml() { return answerXml; }
    public void setAnswerXml(String answerXml) { this.answerXml = answerXml; }

    public String getStemImageRefsJson() { return stemImageRefsJson; }
    public void setStemImageRefsJson(String stemImageRefsJson) { this.stemImageRefsJson = stemImageRefsJson; }

    public String getAnswerImageRefsJson() { return answerImageRefsJson; }
    public void setAnswerImageRefsJson(String answerImageRefsJson) { this.answerImageRefsJson = answerImageRefsJson; }

    public String getStemImagesJson() { return stemImagesJson; }
    public void setStemImagesJson(String stemImagesJson) { this.stemImagesJson = stemImagesJson; }

    public String getAnswerImagesJson() { return answerImagesJson; }
    public void setAnswerImagesJson(String answerImagesJson) { this.answerImagesJson = answerImagesJson; }

    public Boolean getParseError() { return parseError; }
    public void setParseError(Boolean parseError) { this.parseError = parseError; }

    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}