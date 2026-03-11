package io.github.kamill7779.qforge.ocr.service;

import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.internal.api.GaokaoSplitRequest;
import io.github.kamill7779.qforge.internal.api.GaokaoSplitResponse;
import io.github.kamill7779.qforge.ocr.client.ExamImageCropper;
import io.github.kamill7779.qforge.ocr.client.ExamParseOutputParser;
import io.github.kamill7779.qforge.ocr.client.ExamQuestionXmlGenerator;
import io.github.kamill7779.qforge.ocr.client.ExamSplitLlmClient;
import io.github.kamill7779.qforge.ocr.client.MultiPageOcrAggregator;
import io.github.kamill7779.qforge.ocr.entity.ExamParseSourceFile;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 为 gaokao-corpus-service 提供同步整卷分题能力。
 */
@Service
public class GaokaoSplitService {

    private static final Logger log = LoggerFactory.getLogger(GaokaoSplitService.class);

    private final MultiPageOcrAggregator ocrAggregator;
    private final ExamSplitLlmClient splitLlmClient;
    private final ExamParseOutputParser outputParser;
    private final ExamImageCropper imageCropper;
    private final ExamQuestionXmlGenerator xmlGenerator;

    public GaokaoSplitService(MultiPageOcrAggregator ocrAggregator,
                              ExamSplitLlmClient splitLlmClient,
                              ExamParseOutputParser outputParser,
                              ExamImageCropper imageCropper,
                              ExamQuestionXmlGenerator xmlGenerator) {
        this.ocrAggregator = ocrAggregator;
        this.splitLlmClient = splitLlmClient;
        this.outputParser = outputParser;
        this.imageCropper = imageCropper;
        this.xmlGenerator = xmlGenerator;
    }

    public GaokaoSplitResponse split(GaokaoSplitRequest request) {
        List<ExamParseSourceFile> sourceFiles = request.sourceFiles().stream()
                .map(this::toSourceFile)
                .collect(Collectors.toList());

        MultiPageOcrAggregator.AggregationResult aggregationResult = ocrAggregator.aggregate(sourceFiles);
        String llmOutput = splitLlmClient.split(aggregationResult.aggregatedText(), request.hasAnswerHint());
        List<ExamParseOutputParser.ParsedQuestion> parsedQuestions = outputParser.parse(llmOutput);

        List<GaokaoSplitResponse.SplitQuestionEntry> questions = parsedQuestions.stream()
                .map(question -> toSplitQuestion(question, aggregationResult))
                .collect(Collectors.toList());

        log.info("Gaokao split completed: files={}, totalPages={}, questions={}",
                sourceFiles.size(), aggregationResult.totalPages(), questions.size());
        return new GaokaoSplitResponse(questions, aggregationResult.totalPages(), null);
    }

    private GaokaoSplitResponse.SplitQuestionEntry toSplitQuestion(
            ExamParseOutputParser.ParsedQuestion question,
            MultiPageOcrAggregator.AggregationResult aggregationResult) {
        ExamImageCropper.CropResult stemCrop = imageCropper.cropStemImages(
                question.rawStemText(),
                question.stemImageRefs(),
                aggregationResult.imageRegistry(),
                aggregationResult.pageImageMap());
        ExamImageCropper.CropResult answerCrop = imageCropper.cropAnswerImages(
                question.rawAnswerText(),
                question.answerImageRefs(),
                aggregationResult.imageRegistry(),
                aggregationResult.pageImageMap(),
                question.seq());

        ExamQuestionXmlGenerator.XmlResult xmlResult = xmlGenerator.generate(
                question.seq(),
                stemCrop.replacedText(),
                answerCrop.replacedText());

        String combinedError = joinErrors(
                question.errorMsg(),
                xmlResult.errorMsg(),
                xmlResult.stemError() || xmlResult.answerError() ? "XML generation degraded" : null);

        return new GaokaoSplitResponse.SplitQuestionEntry(
                question.seq(),
                question.questionType(),
                question.sourcePages(),
                stemCrop.replacedText(),
                xmlResult.stemXml(),
                answerCrop.replacedText(),
                xmlResult.answerXml(),
                question.stemImageRefs(),
                question.answerImageRefs(),
                toImageMap(stemCrop.images()),
                toImageMap(answerCrop.images()),
                question.parseError() || xmlResult.stemError() || xmlResult.answerError(),
                combinedError);
    }

    private ExamParseSourceFile toSourceFile(GaokaoSplitRequest.SourceFileEntry entry) {
        ExamParseSourceFile sourceFile = new ExamParseSourceFile();
        sourceFile.setTaskUuid("gaokao-inline");
        sourceFile.setFileIndex(entry.fileIndex() != null ? entry.fileIndex() : 0);
        sourceFile.setFileName(entry.fileName());
        sourceFile.setFileType(entry.fileType());
        sourceFile.setFileData(entry.imageBase64());
        sourceFile.setStorageRef(entry.storageRef());
        return sourceFile;
    }

    private Map<String, String> toImageMap(List<ExtractedImage> images) {
        if (images == null || images.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> mapped = new LinkedHashMap<>();
        for (ExtractedImage image : images) {
            mapped.put(image.refKey(), image.imageBase64());
        }
        return mapped;
    }

    private String joinErrors(String... messages) {
        return java.util.Arrays.stream(messages)
                .filter(message -> message != null && !message.isBlank())
                .collect(Collectors.joining("; "));
    }
}