package io.github.kamill7779.qforge.ocr.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 试卷解析专用 OCR 文本预处理器（不修改现有 {@link OcrTextPreprocessor}）。
 * <p>
 * 将 {@code ![](page=N,bbox=[x1,y1,x2,y2])} 替换为全局 ref 格式：
 * {@code <image ref="fig-{globalPage}-{figSeq}" bbox="x1,y1,x2,y2" globalPage="{globalPage}" />}。
 */
@Component
public class ExamPagePreprocessor {

    private static final Logger log = LoggerFactory.getLogger(ExamPagePreprocessor.class);

    /**
     * bbox 正则：支持整数和浮点坐标。
     * 匹配示例：![](page=0,bbox=[226, 241, 419, 364]) 或 ![](page=0,bbox=[226.5, 241.0, 419.5, 364.0])
     */
    private static final Pattern BBOX_PATTERN = Pattern.compile(
            "!\\[.*?]\\(page=(\\d+),\\s*bbox=\\[([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+)]\\)");

    private static final Pattern CAPTION_BLOCK_PATTERN = Pattern.compile(
            "<div\\s+align=\"center\">\\s*\\n?\\s*图\\d+\\s*\\n?\\s*</div>",
            Pattern.CASE_INSENSITIVE);

    /**
     * 全局图片 Registry 条目。
     */
    public record ImageRegistryEntry(
            String ref,
            int globalPage,
            int x1, int y1, int x2, int y2,
            int sourceFileIndex,
            int pageWithinFile
    ) {
        public String toBboxString() {
            return x1 + "," + y1 + "," + x2 + "," + y2;
        }
    }

    /**
     * 预处理结果。
     */
    public record PreprocessResult(
            String cleanedText,
            List<ImageRegistryEntry> imageEntries
    ) {
    }

    /**
     * 对单页 OCR 文本进行预处理，生成全局 ref 格式的图片占位符。
     *
     * @param ocrText         单页 OCR 文本（来自 GLM-OCR layout_parsing）
     * @param globalPage      全局页码（0-based）
     * @param sourceFileIndex 源文件索引
     * @param pageWithinFile  文件内页码（0-based）
     * @return 预处理结果（含清洗文本 + 全局图片 Registry 条目）
     */
    public PreprocessResult preprocess(String ocrText, int globalPage,
                                       int sourceFileIndex, int pageWithinFile) {
        if (ocrText == null || ocrText.isBlank()) {
            return new PreprocessResult(ocrText, Collections.emptyList());
        }

        List<ImageRegistryEntry> entries = new ArrayList<>();
        Matcher matcher = BBOX_PATTERN.matcher(ocrText);

        int figSeq = 1;
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            int x1 = (int) Math.round(Double.parseDouble(matcher.group(2)));
            int y1 = (int) Math.round(Double.parseDouble(matcher.group(3)));
            int x2 = (int) Math.round(Double.parseDouble(matcher.group(4)));
            int y2 = (int) Math.round(Double.parseDouble(matcher.group(5)));

            String ref = "fig-" + globalPage + "-" + figSeq;
            entries.add(new ImageRegistryEntry(ref, globalPage, x1, y1, x2, y2,
                    sourceFileIndex, pageWithinFile));

            String replacement = "<image ref=\"" + ref + "\" bbox=\""
                    + x1 + "," + y1 + "," + x2 + "," + y2
                    + "\" globalPage=\"" + globalPage + "\" />";
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            figSeq++;
        }
        matcher.appendTail(sb);

        String text = sb.toString();
        text = CAPTION_BLOCK_PATTERN.matcher(text).replaceAll("");
        text = text.replaceAll("\\n{3,}", "\n\n").trim();

        log.info("Exam page preprocessed: globalPage={}, {} image entries, text_len {} → {}",
                globalPage, entries.size(), ocrText.length(), text.length());
        if (entries.isEmpty() && ocrText.contains("![")) {
            log.warn("OCR text contains '![' but no bbox matched. Raw snippet: {}",
                    ocrText.substring(0, Math.min(400, ocrText.length())));
        }

        return new PreprocessResult(text, Collections.unmodifiableList(entries));
    }
}
