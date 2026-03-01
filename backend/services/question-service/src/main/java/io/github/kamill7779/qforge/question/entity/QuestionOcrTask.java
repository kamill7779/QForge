package io.github.kamill7779.qforge.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("q_question_ocr_task")
public class QuestionOcrTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_uuid")
    private String taskUuid;

    @TableField("question_uuid")
    private String questionUuid;

    @TableField("biz_type")
    private String bizType;

    private String status;

    @TableField("request_user")
    private String requestUser;

    @TableField("recognized_text")
    private String recognizedText;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("confirmed_text")
    private String confirmedText;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskUuid() {
        return taskUuid;
    }

    public void setTaskUuid(String taskUuid) {
        this.taskUuid = taskUuid;
    }

    public String getQuestionUuid() {
        return questionUuid;
    }

    public void setQuestionUuid(String questionUuid) {
        this.questionUuid = questionUuid;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRequestUser() {
        return requestUser;
    }

    public void setRequestUser(String requestUser) {
        this.requestUser = requestUser;
    }

    public String getRecognizedText() {
        return recognizedText;
    }

    public void setRecognizedText(String recognizedText) {
        this.recognizedText = recognizedText;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }

    public String getConfirmedText() {
        return confirmedText;
    }

    public void setConfirmedText(String confirmedText) {
        this.confirmedText = confirmedText;
    }
}
