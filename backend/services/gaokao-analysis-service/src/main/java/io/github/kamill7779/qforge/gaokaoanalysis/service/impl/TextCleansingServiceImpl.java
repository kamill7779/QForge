package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.service.TextCleansingService;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class TextCleansingServiceImpl implements TextCleansingService {

    private static final Logger log = LoggerFactory.getLogger(TextCleansingServiceImpl.class);

    private static final Pattern MULTI_SPACE = Pattern.compile("[\\s\\u00A0]+");
    private static final Pattern NOISE_CHARS = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]");
    private static final Pattern PUNCTUATION = Pattern.compile("[，。！？；：、\\u201C\\u201D\\u2018\\u2019（）【】《》…—–\\-\\p{Punct}]");

    @Override
    public String cleanStemText(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return "";
        }
        String cleaned = NOISE_CHARS.matcher(rawText).replaceAll("");
        cleaned = MULTI_SPACE.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.strip();
        log.debug("Cleaned stem text: {} chars -> {} chars", rawText.length(), cleaned.length());
        return cleaned;
    }

    @Override
    public String convertToXml(String cleanedText) {
        if (cleanedText == null || cleanedText.isBlank()) {
            return "<stem/>";
        }
        String escaped = cleanedText
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<stem>" + escaped + "</stem>";
    }

    @Override
    public String normalizeForSearch(String stemText) {
        if (stemText == null || stemText.isBlank()) {
            return "";
        }
        String normalized = stemText.toLowerCase();
        normalized = PUNCTUATION.matcher(normalized).replaceAll(" ");
        normalized = MULTI_SPACE.matcher(normalized).replaceAll(" ");
        return normalized.strip();
    }
}
