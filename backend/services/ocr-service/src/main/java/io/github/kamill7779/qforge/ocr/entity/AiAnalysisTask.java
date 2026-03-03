package io.github.kamill7779.qforge.ocr.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

@TableName("q_ai_analysis_task")
public class AiAnalysisTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_uuid")
    private String taskUuid;

    @TableField("question_uuid")
    private String questionUuid;

    private String status;

    private String model;

    @TableField("user_prompt")
    private String userPrompt;

    @TableField("raw_response")
    private String rawResponse;

    @TableField("suggested_tags")
    private String suggestedTags;

    @TableField("suggested_difficulty")
    private BigDecimal suggestedDifficulty;

    private String reasoning;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("request_user")
    private String requestUser;

    // --- Getters & Setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskUuid() { return taskUuid; }
    public void setTaskUuid(String taskUuid) { this.taskUuid = taskUuid; }

    public String getQuestionUuid() { return questionUuid; }
    public void setQuestionUuid(String questionUuid) { this.questionUuid = questionUuid; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String getUserPrompt() { return userPrompt; }
    public void setUserPrompt(String userPrompt) { this.userPrompt = userPrompt; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

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
}
