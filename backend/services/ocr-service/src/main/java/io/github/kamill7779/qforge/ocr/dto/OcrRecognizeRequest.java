package io.github.kamill7779.qforge.ocr.dto;

import jakarta.validation.constraints.NotBlank;

public class OcrRecognizeRequest {

    @NotBlank
    private String imageBase64;

    private String fileType;

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }
}
