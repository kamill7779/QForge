package io.github.kamill7779.qforge.gaokaoanalysis.dto;

import java.util.List;

public class PhotoQueryInternalResponse {

    private String ocrRaw;
    private String stemText;
    private String stemXml;
    private String answerXml;
    private AnalysisResultDTO analysisProfile;
    private List<RecommendGroupDTO> recommendGroups;
    private String reasonSummary;

    public String getOcrRaw() {
        return ocrRaw;
    }

    public void setOcrRaw(String ocrRaw) {
        this.ocrRaw = ocrRaw;
    }

    public String getStemText() {
        return stemText;
    }

    public void setStemText(String stemText) {
        this.stemText = stemText;
    }

    public String getStemXml() {
        return stemXml;
    }

    public void setStemXml(String stemXml) {
        this.stemXml = stemXml;
    }

    public String getAnswerXml() {
        return answerXml;
    }

    public void setAnswerXml(String answerXml) {
        this.answerXml = answerXml;
    }

    public AnalysisResultDTO getAnalysisProfile() {
        return analysisProfile;
    }

    public void setAnalysisProfile(AnalysisResultDTO analysisProfile) {
        this.analysisProfile = analysisProfile;
    }

    public List<RecommendGroupDTO> getRecommendGroups() {
        return recommendGroups;
    }

    public void setRecommendGroups(List<RecommendGroupDTO> recommendGroups) {
        this.recommendGroups = recommendGroups;
    }

    public String getReasonSummary() {
        return reasonSummary;
    }

    public void setReasonSummary(String reasonSummary) {
        this.reasonSummary = reasonSummary;
    }
}
