package io.github.kamill7779.qforge.ocr.mq;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.common.contract.OcrTaskCreatedEvent;
import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.ocr.client.AnswerXmlConverter;
import io.github.kamill7779.qforge.ocr.client.GlmOcrClient;
import io.github.kamill7779.qforge.ocr.client.ImageRegionCropper;
import io.github.kamill7779.qforge.ocr.client.OcrTextPreprocessor;
import io.github.kamill7779.qforge.ocr.client.StemXmlConverter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

@ExtendWith(MockitoExtension.class)
class OcrTaskConsumerAnswerPipelineTest {

    @Mock
    private GlmOcrClient glmOcrClient;
    @Mock
    private ImageRegionCropper imageRegionCropper;
    @Mock
    private StemXmlConverter stemXmlConverter;
    @Mock
    private AnswerXmlConverter answerXmlConverter;
    @Mock
    private RabbitTemplate rabbitTemplate;

    private OcrTaskConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new OcrTaskConsumer(
                glmOcrClient,
                new OcrTextPreprocessor(),
                imageRegionCropper,
                stemXmlConverter,
                answerXmlConverter,
                rabbitTemplate,
                new ObjectMapper()
        );
    }

    @Test
    void shouldPublishAnswerXmlAndCroppedAssetsForAnswerContent() {
        when(glmOcrClient.recognizeText("IMG_BASE64")).thenReturn("""
                answer text
                ![](page=0,bbox=[10, 20, 30, 40])
                """);
        when(answerXmlConverter.convertToAnswerXml(any())).thenReturn(
                "<answer version=\"1\"><p>answer text</p><image ref=\"fig-1\" /></answer>"
        );
        when(imageRegionCropper.crop(eq("IMG_BASE64"), anyList()))
                .thenReturn(List.of(new ExtractedImage("fig-1", "BASE64_CROP", "image/png")));

        consumer.onTaskCreated(new OcrTaskCreatedEvent(
                "a92f6c03-3742-48ca-bf50-2de1678b4118",
                "ANSWER_CONTENT",
                "q-1",
                "IMG_BASE64",
                "admin",
                "2026-03-04T10:00:00Z"
        ));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, atLeast(2)).convertAndSend(any(), any(), payloadCaptor.capture());

        OcrTaskResultEvent result = payloadCaptor.getAllValues().stream()
                .filter(OcrTaskResultEvent.class::isInstance)
                .map(OcrTaskResultEvent.class::cast)
                .findFirst()
                .orElseThrow();

        assertTrue(result.recognizedText().startsWith("<answer"),
                "ANSWER_CONTENT must output answer XML");
        assertNotNull(result.extractedImagesJson(),
                "ANSWER_CONTENT must include extracted images json");
    }

    @Test
    void shouldFallbackToDeterministicAnswerXmlWhenModelReturnsMarkdown() {
        when(glmOcrClient.recognizeText("IMG_BASE64")).thenReturn("""
                answer text
                ![](page=0,bbox=[10, 20, 30, 40])
                """);
        when(answerXmlConverter.convertToAnswerXml(any())).thenReturn("""
                answer text
                ![](page=0,bbox=[10, 20, 30, 40])
                """);
        when(imageRegionCropper.crop(eq("IMG_BASE64"), anyList()))
                .thenReturn(List.of(new ExtractedImage("fig-1", "BASE64_CROP", "image/png")));

        consumer.onTaskCreated(new OcrTaskCreatedEvent(
                "a92f6c03-3742-48ca-bf50-2de1678b4118",
                "ANSWER_CONTENT",
                "q-1",
                "IMG_BASE64",
                "admin",
                "2026-03-04T10:00:00Z"
        ));

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate, atLeast(2)).convertAndSend(any(), any(), payloadCaptor.capture());

        OcrTaskResultEvent result = payloadCaptor.getAllValues().stream()
                .filter(OcrTaskResultEvent.class::isInstance)
                .map(OcrTaskResultEvent.class::cast)
                .findFirst()
                .orElseThrow();

        assertTrue(result.recognizedText().startsWith("<answer"),
                "non-xml model output must fallback to deterministic answer XML");
        assertTrue(result.recognizedText().contains("a92f6c03-img-"),
                "fallback xml must remap image refs to answer-prefixed keys");
        assertFalse(result.recognizedText().contains("ref=\"fig-"),
                "fallback xml must not keep fig-* refs");
        assertFalse(result.recognizedText().contains("![](page="),
                "fallback xml must not keep markdown bbox placeholders");
    }
}
