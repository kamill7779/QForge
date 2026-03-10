package io.github.kamill7779.qforge.ocr.dto;

public class OcrRecognizeResponse {

    private String fullText;
    private String layoutJson;
    private String formulaJson;

    public String getFullText() {
        return fullText;
    }

    public void setFullText(String fullText) {
        this.fullText = fullText;
    }

    public String getLayoutJson() {
        return layoutJson;
    }

    public void setLayoutJson(String layoutJson) {
        this.layoutJson = layoutJson;
    }

    public String getFormulaJson() {
        return formulaJson;
    }

    public void setFormulaJson(String formulaJson) {
        this.formulaJson = formulaJson;
    }
}
