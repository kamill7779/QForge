package io.github.kamill7779.qforge.ocr.client;

import io.github.kamill7779.qforge.ocr.entity.ExamParseSourceFile;
import io.github.kamill7779.qforge.storage.QForgeStorageService;
import java.util.ArrayList;
import java.util.Base64;
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

    /**
     * 试卷解析专用 OCR prompt —— 关键区别：要求保留图片区域 bbox 标记。
     * 默认 prompt 会让 GLM "IGNORE images"，导致 layout_parsing 的 md_results 中
     * 不输出 {@code ![](page=N,bbox=[x1,y1,x2,y2])} 标记 → 无法裁剪配图。
     */
    private static final String EXAM_OCR_PROMPT = String.join("\n",
            "你是一个中国高考数学试卷的文档布局分析器。",
            "1. 忠实提取试卷中的所有文字内容，包括题目、选项、章节标题。",
            "2. 【非常重要】保留所有图片/图形区域的 bbox 坐标标记，以 ![](page=N,bbox=[x1,y1,x2,y2]) 格式输出。",
            "   即使图片内容是几何图形、函数图像、坐标系等，也必须保留其 bbox 标记。",
            "3. 将所有数学公式转换为标准 LaTeX 格式（行内 $...$，独立 $$...$$）。",
            "4. 去除水印、广告、页眉页脚等非题目内容。",
            "5. 仅输出提取到的文本内容和图片 bbox 标记，不要添加说明性文字。"
    );

    private final GlmOcrClient glmOcrClient;
    private final PdfPageRenderer pdfPageRenderer;
    private final ExamPagePreprocessor examPagePreprocessor;
    private final QForgeStorageService storageService;

    public MultiPageOcrAggregator(GlmOcrClient glmOcrClient,
                                   PdfPageRenderer pdfPageRenderer,
                                   ExamPagePreprocessor examPagePreprocessor,
                                   QForgeStorageService storageService) {
        this.glmOcrClient = glmOcrClient;
        this.pdfPageRenderer = pdfPageRenderer;
        this.examPagePreprocessor = examPagePreprocessor;
        this.storageService = storageService;
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

                String ocrText = glmOcrClient.recognizeText(page.imageBase64, EXAM_OCR_PROMPT);
                log.info("GLM OCR returned text (len={}) for globalPage={}",
                        ocrText != null ? ocrText.length() : 0, globalPage);
                // 记录 OCR 文本前 500 字符（排查 bbox 是否输出）
                if (ocrText != null && log.isDebugEnabled()) {
                    log.debug("OCR text preview (globalPage={}): {}", globalPage,
                            ocrText.substring(0, Math.min(500, ocrText.length())));
                }

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
        String filePayload = resolveFilePayload(sf);
        if ("PDF".equalsIgnoreCase(sf.getFileType())) {
            List<PdfPageRenderer.PageImage> pdfPages = pdfPageRenderer.render(filePayload);
            List<PageEntry> entries = new ArrayList<>(pdfPages.size());
            for (PdfPageRenderer.PageImage pi : pdfPages) {
                entries.add(new PageEntry(pi.pageIndex(), pi.imageBase64()));
            }
            return entries;
        }
        // IMAGE type: 单张图片 = 单页
        return List.of(new PageEntry(0, filePayload));
    }

    private String resolveFilePayload(ExamParseSourceFile sourceFile) {
        if (sourceFile.getFileData() != null && !sourceFile.getFileData().isBlank()) {
            return sourceFile.getFileData();
        }
        if (sourceFile.getStorageRef() == null || sourceFile.getStorageRef().isBlank()) {
            throw new IllegalStateException("Missing parse source payload for file: " + sourceFile.getId());
        }
        try {
            return Base64.getEncoder().encodeToString(storageService.getObjectBytes(sourceFile.getStorageRef()));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("Failed to load parse source from storage: " + sourceFile.getStorageRef(), ex);
        }
    }
}
