package io.github.kamill7779.qforge.gaokaoanalysis.dto;

public class PhotoQueryInternalRequest {

    private String imageBase64;
    private String imageStorageRef;
    private String ocrText;

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageStorageRef() {
        return imageStorageRef;
    }

    public void setImageStorageRef(String imageStorageRef) {
        this.imageStorageRef = imageStorageRef;
    }

    public String getOcrText() {
        return ocrText;
    }

    public void setOcrText(String ocrText) {
        this.ocrText = ocrText;
    }
}
