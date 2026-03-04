package io.github.kamill7779.qforge.ocr.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.kamill7779.qforge.ocr.client.OcrTextPreprocessor.BboxRegion;
import io.github.kamill7779.qforge.ocr.client.OcrTextPreprocessor.PreprocessResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OcrTextPreprocessorTest {

    private OcrTextPreprocessor preprocessor;

    @BeforeEach
    void setUp() {
        preprocessor = new OcrTextPreprocessor();
    }

    @Test
    void shouldParseBboxAndReplace() {
        String input = """
                某题目文本
                ![](page=0,bbox=[226, 241, 419, 364])
                <div align="center">
                图1
                </div>
                更多文本
                ![](page=0,bbox=[463, 247, 656, 358])
                <div align="center">
                图2
                </div>
                """;

        PreprocessResult result = preprocessor.preprocess(input);

        List<BboxRegion> regions = result.bboxRegions();
        assertEquals(2, regions.size());

        // 验证 bbox 坐标
        assertEquals(226, regions.get(0).x1());
        assertEquals(241, regions.get(0).y1());
        assertEquals(419, regions.get(0).x2());
        assertEquals(364, regions.get(0).y2());
        assertEquals(0, regions.get(0).page());
        assertEquals(1, regions.get(0).index());

        assertEquals(463, regions.get(1).x1());
        assertEquals(247, regions.get(1).y1());
        assertEquals(656, regions.get(1).x2());
        assertEquals(358, regions.get(1).y2());
        assertEquals(2, regions.get(1).index());

        // 验证替换结果
        String cleaned = result.cleanedText();
        assertTrue(cleaned.contains("<image ref=\"fig-1\" bbox=\"226,241,419,364\" />"));
        assertTrue(cleaned.contains("<image ref=\"fig-2\" bbox=\"463,247,656,358\" />"));

        // 验证图片标签块已移除
        assertTrue(!cleaned.contains("图1"));
        assertTrue(!cleaned.contains("图2"));
        assertTrue(!cleaned.contains("<div align=\"center\">"));
    }

    @Test
    void shouldHandleNoBbox() {
        String input = "普通文本，没有图片";
        PreprocessResult result = preprocessor.preprocess(input);

        assertEquals(0, result.bboxRegions().size());
        assertEquals("普通文本，没有图片", result.cleanedText());
    }

    @Test
    void shouldHandleNullInput() {
        PreprocessResult result = preprocessor.preprocess(null);
        assertTrue(result.bboxRegions().isEmpty());
    }

    @Test
    void shouldHandleBlankInput() {
        PreprocessResult result = preprocessor.preprocess("   ");
        assertTrue(result.bboxRegions().isEmpty());
    }

    @Test
    void shouldCleanExcessiveBlankLines() {
        String input = "文本1\n\n\n\n\n文本2";
        PreprocessResult result = preprocessor.preprocess(input);

        // 三个以上连续换行 → 两个
        assertTrue(!result.cleanedText().contains("\n\n\n"));
        assertTrue(result.cleanedText().contains("文本1\n\n文本2"));
    }

    @Test
    void shouldReturnCorrectBboxString() {
        BboxRegion region = new BboxRegion(1, 0, 100, 200, 300, 400);
        assertEquals("100,200,300,400", region.toBboxString());
    }
}
