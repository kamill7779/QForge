package io.github.kamill7779.qforge.ocr.client;

import io.github.kamill7779.qforge.ocr.entity.ExamParseSourceFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 多文件/多页 OCR 整合器。
 * <p>
 * 逐文件逐页调用 GLM-OCR + {@link ExamPagePreprocessor}，
 * 生成全局文档文本 + 全局图片 Registry。
 */
@Component
public class MultiPageOcrAggregator {

    private static final Logger log = LoggerFactory.getLogger(MultiPageOcrAggregator.class);

    private final GlmOcrClient glmOcrClient;
    private final PdfPageRenderer pdfPageRenderer;
    private final ExamPagePreprocessor examPagePreprocessor;

    public MultiPageOcrAggregator(GlmOcrClient glmOcrClient,
                                   PdfPageRenderer pdfPageRenderer,
                                   ExamPagePreprocessor examPagePreprocessor) {
        this.glmOcrClient = glmOcrClient;
        this.pdfPageRenderer = pdfPageRenderer;
        this.examPagePreprocessor = examPagePreprocessor;
    }

    /**
     * 整合结果。
     */
    public record AggregationResult(
            /** 全局 OCR 文档文本（含 {@code === PAGE N ===} 分隔符） */
            String aggregatedText,
            /** 全局图片 Registry（ref → entry） */
            Map<String, ExamPagePreprocessor.ImageRegistryEntry> imageRegistry,
            /** 全局页码 → 对应的原始 base64 图片（用于后续裁剪） */
            Map<Integer, String> pageImageMap,
            /** 总页数 */
            int totalPages
    ) {
    }

    /**
     * 对多个源文件执行 OCR 并整合为全局文档。
     *
     * @param sourceFiles 按 fileIndex 排序的源文件列表
     * @return 整合结果
     */
    public AggregationResult aggregate(List<ExamParseSourceFile> sourceFiles) {
        StringBuilder aggregatedText = new StringBuilder();
        Map<String, ExamPagePreprocessor.ImageRegistryEntry> imageRegistry = new HashMap<>();
        Map<Integer, String> pageImageMap = new HashMap<>();
        int globalPage = 0;

        for (ExamParseSourceFile sf : sourceFiles) {
            List<PageEntry> pages = expandToPages(sf);

            for (PageEntry page : pages) {
                log.info("OCR processing: file={} ({}), page={}, globalPage={}",
                        sf.getFileName(), sf.getFileType(), page.pageWithinFile, globalPage);

                String ocrText = glmOcrClient.recognizeText(page.imageBase64);
                log.info("GLM OCR returned text (len={}) for globalPage={}",
                        ocrText != null ? ocrText.length() : 0, globalPage);

                ExamPagePreprocessor.PreprocessResult preprocessed =
                        examPagePreprocessor.preprocess(ocrText, globalPage,
                                sf.getFileIndex(), page.pageWithinFile);

                aggregatedText.append("=== PAGE ").append(globalPage).append(" ===\n");
                aggregatedText.append(preprocessed.cleanedText()).append("\n\n");

                for (ExamPagePreprocessor.ImageRegistryEntry entry : preprocessed.imageEntries()) {
                    imageRegistry.put(entry.ref(), entry);
                }

                pageImageMap.put(globalPage, page.imageBase64);
                globalPage++;
            }
        }

        log.info("OCR aggregation complete: {} total pages, {} image entries, text_len={}",
                globalPage, imageRegistry.size(), aggregatedText.length());

        return new AggregationResult(
                aggregatedText.toString().trim(),
                Collections.unmodifiableMap(imageRegistry),
                Collections.unmodifiableMap(pageImageMap),
                globalPage
        );
    }

    private record PageEntry(int pageWithinFile, String imageBase64) {
    }

    private List<PageEntry> expandToPages(ExamParseSourceFile sf) {
        if ("PDF".equalsIgnoreCase(sf.getFileType())) {
            List<PdfPageRenderer.PageImage> pdfPages = pdfPageRenderer.render(sf.getFileData());
            List<PageEntry> entries = new ArrayList<>(pdfPages.size());
            for (PdfPageRenderer.PageImage pi : pdfPages) {
                entries.add(new PageEntry(pi.pageIndex(), pi.imageBase64()));
            }
            return entries;
        }
        // IMAGE type: 单张图片 = 单页
        return List.of(new PageEntry(0, sf.getFileData()));
    }
}
