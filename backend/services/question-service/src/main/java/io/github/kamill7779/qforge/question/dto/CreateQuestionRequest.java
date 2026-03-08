package io.github.kamill7779.qforge.question.dto;

public class CreateQuestionRequest {

    private String stemText;
    private String source;

    public String getStemText() {
        return stemText;
    }

    public void setStemText(String stemText) {
        this.stemText = stemText;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

