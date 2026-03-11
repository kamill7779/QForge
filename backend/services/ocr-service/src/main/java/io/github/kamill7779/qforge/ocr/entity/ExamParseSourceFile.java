package io.github.kamill7779.qforge.ocr.entity;

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

    @TableField("storage_ref")
    private String storageRef;

    @TableField("blob_key")
    private String blobKey;

    @TableField("blob_size")
    private Long blobSize;

    @TableField("checksum_sha256")
    private String checksumSha256;

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

    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }

    public String getBlobKey() { return blobKey; }
    public void setBlobKey(String blobKey) { this.blobKey = blobKey; }

    public Long getBlobSize() { return blobSize; }
    public void setBlobSize(Long blobSize) { this.blobSize = blobSize; }

    public String getChecksumSha256() { return checksumSha256; }
    public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }

    public String getOcrStatus() { return ocrStatus; }
    public void setOcrStatus(String ocrStatus) { this.ocrStatus = ocrStatus; }
}
