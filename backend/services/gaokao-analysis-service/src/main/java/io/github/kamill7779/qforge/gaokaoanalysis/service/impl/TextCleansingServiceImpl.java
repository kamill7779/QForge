package io.github.kamill7779.qforge.gaokaoanalysis.service.impl;

import io.github.kamill7779.qforge.gaokaoanalysis.service.TextCleansingService;
import org.springframework.stereotype.Service;

@Service
public class TextCleansingServiceImpl implements TextCleansingService {

    @Override
    public String cleanStemText(String rawText) {
        // TODO: implement — remove noise, normalize whitespace, fix encoding
        throw new UnsupportedOperationException("TextCleansingService.cleanStemText not implemented");
    }

    @Override
    public String convertToXml(String cleanedText) {
        // TODO: implement — convert cleaned text to normalized XML
        throw new UnsupportedOperationException("TextCleansingService.convertToXml not implemented");
    }

    @Override
    public String normalizeForSearch(String stemText) {
        // TODO: implement — lowercase, strip punctuation for search/embedding
        throw new UnsupportedOperationException("TextCleansingService.normalizeForSearch not implemented");
    }
}
