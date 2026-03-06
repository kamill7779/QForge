package io.github.kamill7779.qforge.ocr.client;

public interface GlmOcrClient {

    String recognizeText(String imageBase64);

    /**
     * 使用自定义 prompt 进行 OCR 识别。
     * 试卷解析场景需要保留 bbox 图片标记，因此使用不同于默认的 prompt。
     */
    default String recognizeText(String imageBase64, String customPrompt) {
        return recognizeText(imageBase64);
    }
}

