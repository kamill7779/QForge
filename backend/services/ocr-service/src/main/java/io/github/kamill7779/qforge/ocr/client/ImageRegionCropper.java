package io.github.kamill7779.qforge.ocr.client;

import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 根据 bbox 坐标从原始图片中裁剪出子区域。
 *
 * <p>OCR {@code layout_parsing} 返回 {@code ![](page=0,bbox=[x1,y1,x2,y2])} 格式
 * 的像素级标记，经 {@link OcrTextPreprocessor} 解析后得到 {@link OcrTextPreprocessor.BboxRegion}。
 * 本类接收原始 base64 图片 + bbox 列表，裁剪出每块区域并编码为 PNG base64。</p>
 */
@Component
public class ImageRegionCropper {

    private static final Logger log = LoggerFactory.getLogger(ImageRegionCropper.class);

    /**
     * 裁剪图片区域。
     *
     * @param imageBase64 原始图片 base64（可带或不带 data URL 前缀）
     * @param regions     预处理器提取的 bbox 区域列表
     * @return 裁剪结果列表；解码失败或无区域时返回空列表
     */
    public List<ExtractedImage> crop(String imageBase64,
                                     List<OcrTextPreprocessor.BboxRegion> regions) {
        if (imageBase64 == null || imageBase64.isBlank() || regions == null || regions.isEmpty()) {
            return Collections.emptyList();
        }

        BufferedImage fullImage;
        try {
            String raw = stripDataUrlPrefix(imageBase64);
            byte[] imageBytes = Base64.getDecoder().decode(raw);
            fullImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (fullImage == null) {
                log.warn("ImageIO.read returned null — unsupported image format");
                return Collections.emptyList();
            }
        } catch (Exception ex) {
            log.error("Failed to decode original image for cropping: {}", ex.getMessage());
            return Collections.emptyList();
        }

        int imgW = fullImage.getWidth();
        int imgH = fullImage.getHeight();
        log.info("Cropping {} regions from image {}x{}", regions.size(), imgW, imgH);

        List<ExtractedImage> result = new ArrayList<>();
        for (OcrTextPreprocessor.BboxRegion region : regions) {
            try {
                // Clamp to image bounds
                int x = Math.max(0, region.x1());
                int y = Math.max(0, region.y1());
                int x2 = Math.min(imgW, region.x2());
                int y2 = Math.min(imgH, region.y2());
                int w = x2 - x;
                int h = y2 - y;

                if (w <= 0 || h <= 0) {
                    log.warn("Invalid crop region for fig-{}: x={},y={},w={},h={} (image={}x{})",
                            region.index(), x, y, w, h, imgW, imgH);
                    continue;
                }

                BufferedImage cropped = fullImage.getSubimage(x, y, w, h);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(cropped, "png", baos);
                String croppedBase64 = Base64.getEncoder().encodeToString(baos.toByteArray());

                result.add(new ExtractedImage("fig-" + region.index(), croppedBase64, "image/png"));
                log.info("Cropped fig-{}: {}x{} from bbox [{},{},{},{}]",
                        region.index(), w, h, region.x1(), region.y1(), region.x2(), region.y2());
            } catch (Exception ex) {
                log.warn("Failed to crop fig-{}: {}", region.index(), ex.getMessage());
            }
        }
        return result;
    }

    /** 去除 data URL 前缀（如 {@code data:image/png;base64,}）。 */
    private String stripDataUrlPrefix(String base64) {
        int commaIdx = base64.indexOf(',');
        if (commaIdx > 0 && commaIdx < 100 && base64.startsWith("data:")) {
            return base64.substring(commaIdx + 1);
        }
        return base64;
    }
}
