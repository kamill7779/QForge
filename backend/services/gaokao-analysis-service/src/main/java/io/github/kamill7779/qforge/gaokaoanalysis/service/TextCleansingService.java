package io.github.kamill7779.qforge.gaokaoanalysis.service;

public interface TextCleansingService {

    /**
     * Clean raw stem text: remove noise, normalize whitespace, fix encoding.
     */
    String cleanStemText(String rawText);

    /**
     * Convert cleaned text to normalized XML representation.
     */
    String convertToXml(String cleanedText);

    /**
     * Normalize stem text for search/embedding: lowercase, strip punctuation, etc.
     */
    String normalizeForSearch(String stemText);
}
