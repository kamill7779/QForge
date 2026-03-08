package io.github.kamill7779.qforge.question.dto.exam;

/**
 * 创建试卷请求。
 */
public class CreateExamPaperRequest {

    private String title;
    private String subtitle;
    private String description;
    private Integer durationMinutes;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getSubtitle() { return subtitle; }
    public void setSubtitle(String subtitle) { this.subtitle = subtitle; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
}
