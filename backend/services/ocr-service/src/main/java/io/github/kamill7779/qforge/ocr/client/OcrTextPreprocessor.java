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
 * OCR 文本预处理器：从 layout_parsing 返回的 Markdown 文本中解析 bbox 图片标记，
 * 清理 Markdown 格式，并将 {@code ![](page=0,bbox=[x1,y1,x2,y2])} 替换为
 * {@code <image ref="fig-N" bbox="x1,y1,x2,y2" />} 标记供下游 StemXml 和图片裁剪使用。
 *
 * <h3>OCR 输出中的 bbox 格式示例</h3>
 * <pre>
 * 某些题目文本...
 * ![](page=0,bbox=[226, 241, 419, 364])
 * &lt;div align="center"&gt;
 * 图1
 * &lt;/div&gt;
 * </pre>
 */
@Component
public class OcrTextPreprocessor {

    private static final Logger log = LoggerFactory.getLogger(OcrTextPreprocessor.class);

    /**
     * 匹配 bbox 标记：{@code ![...](page=N,bbox=[x1, y1, x2, y2])}
     * 支持整数和浮点坐标。
     */
    private static final Pattern BBOX_PATTERN = Pattern.compile(
            "!\\[.*?]\\(page=(\\d+),\\s*bbox=\\[([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+),\\s*([\\d.]+)]\\)");

    /**
     * 匹配图片标签块：{@code <div align="center">\n图N\n</div>}
     */
    private static final Pattern CAPTION_BLOCK_PATTERN = Pattern.compile(
            "<div\\s+align=\"center\">\\s*\\n?\\s*图\\d+\\s*\\n?\\s*</div>",
            Pattern.CASE_INSENSITIVE);

    /**
     * 预处理结果：包含清理后的文本和提取的 bbox 列表。
     */
    public record PreprocessResult(
            /** 清理后的文本（bbox 标记已替换为 image 占位符） */
            String cleanedText,
            /** 提取的 bbox 信息列表 */
            List<BboxRegion> bboxRegions
    ) {}

    /**
     * 单个 bbox 图片区域。
     */
    public record BboxRegion(
            /** 图片序号 (1-based) */
            int index,
            /** 页码 (0-based) */
            int page,
            /** 左上角 x */
            int x1,
            /** 左上角 y */
            int y1,
            /** 右下角 x */
            int x2,
            /** 右下角 y */
            int y2
    ) {
        /** 返回 "x1,y1,x2,y2" 格式 */
        public String toBboxString() {
            return x1 + "," + y1 + "," + x2 + "," + y2;
        }
    }

    /**
     * 预处理 OCR 文本：
     * <ol>
     *     <li>提取所有 bbox 区域</li>
     *     <li>将 {@code ![](page=0,bbox=[...])} 替换为 {@code <image ref="fig-N" bbox="..." />}</li>
     *     <li>移除 {@code <div align="center">图N</div>} 标签块</li>
     *     <li>清理多余空行</li>
     * </ol>
     *
     * @param ocrText layout_parsing 返回的原始 Markdown 文本
     * @return 预处理结果
     */
    public PreprocessResult preprocess(String ocrText) {
        if (ocrText == null || ocrText.isBlank()) {
            return new PreprocessResult(ocrText, Collections.emptyList());
        }

        // 1. 提取 bbox 区域
        List<BboxRegion> regions = new ArrayList<>();
        Matcher matcher = BBOX_PATTERN.matcher(ocrText);
        int figIndex = 1;
        while (matcher.find()) {
            regions.add(new BboxRegion(
                    figIndex++,
                    Integer.parseInt(matcher.group(1)),
                    (int) Math.round(Double.parseDouble(matcher.group(2))),
                    (int) Math.round(Double.parseDouble(matcher.group(3))),
                    (int) Math.round(Double.parseDouble(matcher.group(4))),
                    (int) Math.round(Double.parseDouble(matcher.group(5)))
            ));
        }

        // 2. 替换 bbox 标记为 image 占位符
        String text = ocrText;
        matcher = BBOX_PATTERN.matcher(text);
        StringBuilder sb = new StringBuilder();
        int idx = 1;
        while (matcher.find()) {
            BboxRegion region = regions.get(idx - 1);
            matcher.appendReplacement(sb, "<image ref=\"fig-" + idx + "\" bbox=\"" + region.toBboxString() + "\" />");
            idx++;
        }
        matcher.appendTail(sb);
        text = sb.toString();

        // 3. 移除图片标签块
        text = CAPTION_BLOCK_PATTERN.matcher(text).replaceAll("");

        // 4. 清理多余空行（三个以上连续换行 → 两个）
        text = text.replaceAll("\\n{3,}", "\n\n").trim();

        log.info("OCR text preprocessed: {} bbox regions found, text_len {} → {}",
                regions.size(), ocrText.length(), text.length());

        return new PreprocessResult(text, Collections.unmodifiableList(regions));
    }
}
