package io.github.kamill7779.qforge.gaokaocorpus.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("gk_ingest_ocr_page")
public class GkIngestOcrPage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("session_id")
    private Long sessionId;

    @TableField("source_file_id")
    private Long sourceFileId;

    @TableField("page_no")
    private Integer pageNo;

    @TableField("full_text")
    private String fullText;

    @TableField("layout_json")
    private String layoutJson;

    @TableField("formula_json")
    private String formulaJson;

    @TableField("page_image_ref")
    private String pageImageRef;

    @TableField("created_at")
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }

    public Long getSourceFileId() { return sourceFileId; }
    public void setSourceFileId(Long sourceFileId) { this.sourceFileId = sourceFileId; }

    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }

    public String getFullText() { return fullText; }
    public void setFullText(String fullText) { this.fullText = fullText; }

    public String getLayoutJson() { return layoutJson; }
    public void setLayoutJson(String layoutJson) { this.layoutJson = layoutJson; }

    public String getFormulaJson() { return formulaJson; }
    public void setFormulaJson(String formulaJson) { this.formulaJson = formulaJson; }

    public String getPageImageRef() { return pageImageRef; }
    public void setPageImageRef(String pageImageRef) { this.pageImageRef = pageImageRef; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
