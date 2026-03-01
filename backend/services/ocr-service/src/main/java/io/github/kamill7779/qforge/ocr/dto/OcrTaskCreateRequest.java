package io.github.kamill7779.qforge.ocr.dto;

import jakarta.validation.constraints.NotBlank;

public class OcrTaskCreateRequest {

    @NotBlank
    private String bizType;

    @NotBlank
    private String bizId;

    @NotBlank
    private String imageBase64;

    @NotBlank
    private String requestUser;

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }

    public String getBizId() {
        return bizId;
    }

    public void setBizId(String bizId) {
        this.bizId = bizId;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getRequestUser() {
        return requestUser;
    }

    public void setRequestUser(String requestUser) {
        this.requestUser = requestUser;
    }
}

