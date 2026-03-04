package io.github.kamill7779.qforge.persist.entity;

import com.baomidou.mybatisplus.annotation.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@TableName("q_question_ai_task")
public class QuestionAiTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_uuid")
    private String taskUuid;

    @TableField("question_uuid")
    private String questionUuid;

    private String status;

    @TableField("suggested_tags")
    private String suggestedTags;

    @TableField("suggested_difficulty")
    private BigDecimal suggestedDifficulty;

    private String reasoning;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("request_user")
    private String requestUser;

    @TableField("applied_at")
    private LocalDateTime appliedAt;

    // --- getters & setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTaskUuid() { return taskUuid; }
    public void setTaskUuid(String taskUuid) { this.taskUuid = taskUuid; }
    public String getQuestionUuid() { return questionUuid; }
    public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSuggestedTags() { return suggestedTags; }
    public void setSuggestedTags(String suggestedTags) { this.suggestedTags = suggestedTags; }
    public BigDecimal getSuggestedDifficulty() { return suggestedDifficulty; }
    public void setSuggestedDifficulty(BigDecimal suggestedDifficulty) { this.suggestedDifficulty = suggestedDifficulty; }
    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }
    public String getErrorMsg() { return errorMsg; }
    public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }
    public String getRequestUser() { return requestUser; }
    public void setRequestUser(String requestUser) { this.requestUser = requestUser; }
    public LocalDateTime getAppliedAt() { return appliedAt; }
    public void setAppliedAt(LocalDateTime appliedAt) { this.appliedAt = appliedAt; }
}
