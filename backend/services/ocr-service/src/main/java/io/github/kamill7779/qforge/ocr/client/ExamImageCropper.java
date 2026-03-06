package io.github.kamill7779.qforge.ocr.client;

import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 试卷解析图片裁剪适配器。
 * <p>
 * 基于全局 Registry 和 page→image 映射，按题裁剪图片并替换 ref。
 */
@Component
public class ExamImageCropper {

    private static final Logger log = LoggerFactory.getLogger(ExamImageCropper.class);
    private static final Pattern IMAGE_TAG_PATTERN = Pattern.compile(
            "<image\\s+ref=\"(fig-[^\"]+)\"[^/]*/>");

    private final ImageRegionCropper imageRegionCropper;

    public ExamImageCropper(ImageRegionCropper imageRegionCropper) {
        this.imageRegionCropper = imageRegionCropper;
    }

    /**
     * 图片裁剪结果。
     */
    public record CropResult(
            /** ref 替换后的文本 */
            String replacedText,
            /** 裁剪后的图片列表（ref 已为最终格式） */
            List<ExtractedImage> images
    ) {
    }

    /**
     * 裁剪题干图片（ref 格式：img-{i}）。
     */
    public CropResult cropStemImages(
            String rawStemText,
            List<String> stemImageRefs,
            Map<String, ExamPagePreprocessor.ImageRegistryEntry> imageRegistry,
            Map<Integer, String> pageImageMap) {
        return cropImages(rawStemText, stemImageRefs, imageRegistry, pageImageMap, "img-");
    }

    /**
     * 裁剪答案图片（ref 格式：a{seqNo}-img-{j}）。
     */
    public CropResult cropAnswerImages(
            String rawAnswerText,
            List<String> answerImageRefs,
            Map<String, ExamPagePreprocessor.ImageRegistryEntry> imageRegistry,
            Map<Integer, String> pageImageMap,
            int seqNo) {
        return cropImages(rawAnswerText, answerImageRefs, imageRegistry, pageImageMap,
                "a" + seqNo + "-img-");
    }

    private CropResult cropImages(
            String text,
            List<String> imageRefs,
            Map<String, ExamPagePreprocessor.ImageRegistryEntry> imageRegistry,
            Map<Integer, String> pageImageMap,
            String refPrefix) {

        if (text == null || text.isBlank() || imageRefs == null || imageRefs.isEmpty()) {
            return new CropResult(text, Collections.emptyList());
        }

        List<ExtractedImage> images = new ArrayList<>();
        String replacedText = text;
        int idx = 1;

        for (String ref : imageRefs) {
            ExamPagePreprocessor.ImageRegistryEntry entry = imageRegistry.get(ref);
            if (entry == null) {
                log.warn("Image ref '{}' not found in registry, skipping", ref);
                continue;
            }

            String pageBase64 = pageImageMap.get(entry.globalPage());
            if (pageBase64 == null) {
                log.warn("Page image for globalPage={} not found, skipping ref '{}'",
                        entry.globalPage(), ref);
                continue;
            }

            // 创建 BboxRegion 用于 ImageRegionCropper
            OcrTextPreprocessor.BboxRegion bboxRegion = new OcrTextPreprocessor.BboxRegion(
                    idx, entry.globalPage(),
                    entry.x1(), entry.y1(), entry.x2(), entry.y2());

            List<ExtractedImage> cropped = imageRegionCropper.crop(pageBase64,
                    List.of(bboxRegion));

            String finalRef = refPrefix + idx;
            if (!cropped.isEmpty()) {
                ExtractedImage original = cropped.get(0);
                images.add(new ExtractedImage(finalRef, original.imageBase64(), original.mimeType()));
            }

            // 替换文本中的 ref（保留原 ref 标签格式，替换为最终 ref）
            if (replacedText.contains("ref=\"" + ref + "\"")) {
                replacedText = replacedText.replace(
                        "ref=\"" + ref + "\"",
                        "ref=\"" + finalRef + "\"");
            } else if (!cropped.isEmpty()) {
                // LLM 拆题时可能丢失 <image> 标签，但通过 ###STEM_IMAGES### 声明了 ref。
                // 将图片标签注入到文本末尾，确保下游能渲染。
                replacedText = replacedText + "\n<image ref=\"" + finalRef + "\" />";
                log.info("Injected missing <image ref='{}'/> tag into text for ref '{}'", finalRef, ref);
            }

            idx++;
        }

        log.info("Cropped {} images with prefix '{}'", images.size(), refPrefix);
        return new CropResult(replacedText, images);
    }
}
