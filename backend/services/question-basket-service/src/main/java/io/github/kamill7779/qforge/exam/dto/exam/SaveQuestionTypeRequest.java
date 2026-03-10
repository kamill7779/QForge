package io.github.kamill7779.qforge.exam.dto.exam;

public class SaveQuestionTypeRequest {

    private String typeCode;
    private String typeLabel;
    private String xmlHint;
    private Integer sortOrder;

    public String getTypeCode() { return typeCode; }
    public void setTypeCode(String typeCode) { this.typeCode = typeCode; }
    public String getTypeLabel() { return typeLabel; }
    public void setTypeLabel(String typeLabel) { this.typeLabel = typeLabel; }
    public String getXmlHint() { return xmlHint; }
    public void setXmlHint(String xmlHint) { this.xmlHint = xmlHint; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
}
