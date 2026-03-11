package io.github.kamill7779.qforge.gaokaocorpus.dto;

public class DraftQuestionAssetDTO {

    private String refKey;
    private String assetType;
    private String storageRef;
    private Integer sortOrder;
    private String imageBase64;

    public String getRefKey() { return refKey; }
    public void setRefKey(String refKey) { this.refKey = refKey; }

    public String getAssetType() { return assetType; }
    public void setAssetType(String assetType) { this.assetType = assetType; }

    public String getStorageRef() { return storageRef; }
    public void setStorageRef(String storageRef) { this.storageRef = storageRef; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getImageBase64() { return imageBase64; }
    public void setImageBase64(String imageBase64) { this.imageBase64 = imageBase64; }
}