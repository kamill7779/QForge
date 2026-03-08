package io.github.kamill7779.qforge.question.dto.exam;

/**
 * 更新试卷元信息请求。
 */
public class UpdateExamPaperRequest {

    private String title;
    private String subtitle;
    private String description;
    private Integer durationMinutes;
    private String status;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
