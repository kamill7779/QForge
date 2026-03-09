package io.github.kamill7779.qforge.gaokaoanalysis.dto;

public class PhotoQueryInternalRequest {

    private String imageBase64;
    private String storageRef;
    private String ocrText;
    private Integer topK;

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageStorageRef() {
        return storageRef;
    }

    public void setImageStorageRef(String imageStorageRef) {
        this.storageRef = imageStorageRef;
    }

    public String getStorageRef() {
        return storageRef;
    }

    public void setStorageRef(String storageRef) {
        this.storageRef = storageRef;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }

    public Integer getTopK() {
        return topK;
    }

    public void setTopK(Integer topK) {
        this.topK = topK;
    }
}
