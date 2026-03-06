package io.github.kamill7779.qforge.question.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("q_exam_parse_source_file")
public class ExamParseSourceFile {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("task_uuid")
    private String taskUuid;

    @TableField("file_index")
    private Integer fileIndex;

    @TableField("file_name")
    private String fileName;

    @TableField("file_type")
    private String fileType;

    @TableField("page_count")
    private Integer pageCount;

    @TableField("file_data")
    private String fileData;

    @TableField("ocr_status")
    private String ocrStatus;

    // --- getters & setters ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getTaskUuid() { return taskUuid; }
    public void setTaskUuid(String taskUuid) { this.taskUuid = taskUuid; }

    public Integer getFileIndex() { return fileIndex; }
    public void setFileIndex(Integer fileIndex) { this.fileIndex = fileIndex; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }

    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }

    public String getFileData() { return fileData; }
    public void setFileData(String fileData) { this.fileData = fileData; }

    public String getOcrStatus() { return ocrStatus; }
    public void setOcrStatus(String ocrStatus) { this.ocrStatus = ocrStatus; }
}
