package io.github.kamill7779.qforge.ocr.client;

import io.github.kamill7779.qforge.ocr.config.QForgeOcrProperties;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * PDF → 逐页 PNG base64（使用 PDFBox 3.0.2）。
 */
@Component
public class PdfPageRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfPageRenderer.class);

    private final QForgeOcrProperties ocrProps;

    /**
     * 单页渲染结果。
     */
    public record PageImage(
            int pageIndex,
            String imageBase64,
            String mimeType
    ) {
    }

    public PdfPageRenderer(QForgeOcrProperties ocrProps) {
        this.ocrProps = ocrProps;
    }

    /**
     * 将 PDF base64 渲染为逐页 PNG base64。
     *
     * @param pdfBase64 PDF 文件的 base64 编码
     * @return 每页的 PNG base64 列表
     */
    public List<PageImage> render(String pdfBase64) {
        if (pdfBase64 == null || pdfBase64.isBlank()) {
            return Collections.emptyList();
        }

        byte[] pdfBytes = Base64.getDecoder().decode(stripDataUrlPrefix(pdfBase64));
        int dpi = ocrProps.getPdfRenderDpi();
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            int pageCount = doc.getNumberOfPages();
            log.info("PDF loaded: {} pages, rendering at {} DPI", pageCount, dpi);

            PDFRenderer renderer = new PDFRenderer(doc);
            List<PageImage> pages = new ArrayList<>(pageCount);

            for (int i = 0; i < pageCount; i++) {
                BufferedImage img = renderer.renderImageWithDPI(i, dpi, ImageType.RGB);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
                pages.add(new PageImage(i, base64, "image/png"));
                log.debug("Rendered PDF page {}/{}: {}x{}", i + 1, pageCount,
                        img.getWidth(), img.getHeight());
            }
            return pages;
        } catch (Exception ex) {
            throw new RuntimeException("Failed to render PDF pages: " + ex.getMessage(), ex);
        }
    }

    private String stripDataUrlPrefix(String base64) {
        int commaIdx = base64.indexOf(',');
        if (commaIdx > 0 && commaIdx < 100 && base64.startsWith("data:")) {
            return base64.substring(commaIdx + 1);
        }
        return base64;
    }
}
