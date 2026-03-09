package io.github.kamill7779.qforge.gaokaocorpus.dto;

public class PhotoQueryRequest {

    private String imageBase64;
    private String storageRef;
    private Integer topK;

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }

    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }

    public Integer getTopK() { return topK; }
    public void setTopK(Integer topK) { this.topK = topK; }
}
