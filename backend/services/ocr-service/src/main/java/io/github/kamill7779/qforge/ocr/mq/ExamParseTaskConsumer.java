package io.github.kamill7779.qforge.ocr.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.ExamParseTaskCreatedEvent;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.ocr.client.ExamImageCropper;
import io.github.kamill7779.qforge.ocr.client.ExamPagePreprocessor;
import io.github.kamill7779.qforge.ocr.client.ExamParseOutputParser;
import io.github.kamill7779.qforge.ocr.client.ExamParseResultPublisher;
import io.github.kamill7779.qforge.ocr.client.ExamQuestionXmlGenerator;
import io.github.kamill7779.qforge.ocr.client.ExamSplitLlmClient;
import io.github.kamill7779.qforge.ocr.client.MultiPageOcrAggregator;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.ocr.entity.ExamParseSourceFile;
import io.github.kamill7779.qforge.ocr.entity.ExamParseTask;
import io.github.kamill7779.qforge.ocr.repository.ExamParseSourceFileRepository;
import io.github.kamill7779.qforge.ocr.repository.ExamParseTaskRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 试卷自动解析 MQ 消费者 —— 驱动整个解析流程（OCR → LLM 拆题 → 图片裁剪 → XML 生成 → 发布结果）。
 */
@Component
public class ExamParseTaskConsumer {

    private static final Logger log = LoggerFactory.getLogger(ExamParseTaskConsumer.class);

    private final ExamParseTaskRepository taskRepository;
    private final ExamParseSourceFileRepository sourceFileRepository;
    private final MultiPageOcrAggregator ocrAggregator;
    private final ExamSplitLlmClient splitLlmClient;
    private final ExamParseOutputParser outputParser;
    private final ExamImageCropper imageCropper;
    private final ExamQuestionXmlGenerator xmlGenerator;
    private final ExamParseResultPublisher resultPublisher;
    private final ObjectMapper objectMapper;

    public ExamParseTaskConsumer(
            ExamParseTaskRepository taskRepository,
            ExamParseSourceFileRepository sourceFileRepository,
            MultiPageOcrAggregator ocrAggregator,
            ExamSplitLlmClient splitLlmClient,
            ExamParseOutputParser outputParser,
            ExamImageCropper imageCropper,
            ExamQuestionXmlGenerator xmlGenerator,
            ExamParseResultPublisher resultPublisher,
            ObjectMapper objectMapper) {
        this.taskRepository = taskRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.ocrAggregator = ocrAggregator;
        this.splitLlmClient = splitLlmClient;
        this.outputParser = outputParser;
        this.imageCropper = imageCropper;
        this.xmlGenerator = xmlGenerator;
        this.resultPublisher = resultPublisher;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitTopologyConfig.EXAM_PARSE_TASK_QUEUE)
    public void onExamParseTask(ExamParseTaskCreatedEvent event) {
        String taskUuid = event.taskUuid();
        log.info("Received exam parse task: taskUuid={}, files={}", taskUuid, event.files().size());

        try {
            // 1. 读取源文件
            updateTaskStatus(taskUuid, "OCR_PROCESSING", 5, null);
            List<ExamParseSourceFile> sourceFiles =
                    sourceFileRepository.findByTaskUuidOrderByFileIndex(taskUuid);
            if (sourceFiles.isEmpty()) {
                throw new RuntimeException("No source files found for task: " + taskUuid);
            }

            // 2. 多文件/多页 OCR + 整合
            MultiPageOcrAggregator.AggregationResult ocrResult = ocrAggregator.aggregate(sourceFiles);
            updateTaskStatus(taskUuid, "OCR_PROCESSING", 40, null);
            updateTotalPages(taskUuid, ocrResult.totalPages());

            // 3. LLM 拆题
            updateTaskStatus(taskUuid, "SPLITTING", 50, null);
            String llmOutput = splitLlmClient.split(ocrResult.aggregatedText(), event.hasAnswerHint());

            // 4. 解析 LLM 输出
            List<ExamParseOutputParser.ParsedQuestion> parsedQuestions = outputParser.parse(llmOutput);
            if (parsedQuestions.isEmpty()) {
                throw new RuntimeException("LLM returned no questions for task: " + taskUuid);
            }
            updateTaskStatus(taskUuid, "GENERATING", 60, null);

            // 5. 逐题处理（图片裁剪 + XML 生成 + 发布）
            int successCount = 0;
            int errorCount = 0;

            for (int i = 0; i < parsedQuestions.size(); i++) {
                ExamParseOutputParser.ParsedQuestion pq = parsedQuestions.get(i);
                try {
                    processQuestion(taskUuid, pq, ocrResult.imageRegistry(), ocrResult.pageImageMap());
                    successCount++;
                } catch (Exception ex) {
                    log.error("Failed to process question seq={} for task={}: {}",
                            pq.seq(), taskUuid, ex.getMessage(), ex);
                    // 发布错误结果
                    resultPublisher.publishQuestionResult(
                            taskUuid, pq.seq(), pq.questionType(),
                            pq.rawStemText(), null,
                            pq.rawAnswerText(), null,
                            Collections.emptyList(), Collections.emptyList(),
                            pq.sourcePages(), true, ex.getMessage());
                    errorCount++;
                }

                // 更新进度 60% → 95%
                int progress = 60 + (int) ((i + 1.0) / parsedQuestions.size() * 35);
                updateTaskStatus(taskUuid, "GENERATING", Math.min(progress, 95), null);
            }

            // 6. 发布完成事件
            String finalStatus = errorCount == 0 ? "SUCCESS"
                    : (successCount > 0 ? "PARTIAL_FAILED" : "FAILED");
            updateTaskStatus(taskUuid, finalStatus, 100, null);
            updateQuestionCount(taskUuid, parsedQuestions.size());
            resultPublisher.publishCompleted(taskUuid, finalStatus, parsedQuestions.size(), null);

            log.info("Exam parse task completed: taskUuid={}, status={}, total={}, success={}, errors={}",
                    taskUuid, finalStatus, parsedQuestions.size(), successCount, errorCount);

        } catch (Exception ex) {
            log.error("Exam parse task failed: taskUuid={}, error={}", taskUuid, ex.getMessage(), ex);
            updateTaskStatus(taskUuid, "FAILED", 0,
                    truncate(ex.getMessage(), 2000));
            resultPublisher.publishCompleted(taskUuid, "FAILED", 0, ex.getMessage());
        }
    }

    private void processQuestion(
            String taskUuid,
            ExamParseOutputParser.ParsedQuestion pq,
            Map<String, ExamPagePreprocessor.ImageRegistryEntry> imageRegistry,
            Map<Integer, String> pageImageMap) {

        log.info("Processing question seq={} for task={}", pq.seq(), taskUuid);

        // 图片裁剪（题干）
        ExamImageCropper.CropResult stemCrop = imageCropper.cropStemImages(
                pq.rawStemText(), pq.stemImageRefs(), imageRegistry, pageImageMap);

        // 图片裁剪（答案）
        ExamImageCropper.CropResult answerCrop = imageCropper.cropAnswerImages(
                pq.rawAnswerText(), pq.answerImageRefs(), imageRegistry, pageImageMap, pq.seq());

        // XML 生成
        ExamQuestionXmlGenerator.XmlResult xmlResult = xmlGenerator.generate(
                pq.seq(), stemCrop.replacedText(), answerCrop.replacedText());

        boolean hasError = pq.parseError() || xmlResult.stemError() || xmlResult.answerError();
        String errorMsg = buildErrorMsg(pq, xmlResult);

        // 发布单题结果
        resultPublisher.publishQuestionResult(
                taskUuid,
                pq.seq(),
                pq.questionType(),
                stemCrop.replacedText(),
                xmlResult.stemXml(),
                answerCrop.replacedText(),
                xmlResult.answerXml(),
                stemCrop.images(),
                answerCrop.images(),
                pq.sourcePages(),
                hasError,
                errorMsg
        );
    }

    private String buildErrorMsg(ExamParseOutputParser.ParsedQuestion pq,
                                  ExamQuestionXmlGenerator.XmlResult xmlResult) {
        StringBuilder sb = new StringBuilder();
        if (pq.parseError() && pq.errorMsg() != null) {
            sb.append("Parse: ").append(pq.errorMsg());
        }
        if (xmlResult.errorMsg() != null) {
            if (!sb.isEmpty()) sb.append("; ");
            sb.append(xmlResult.errorMsg());
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private void updateTaskStatus(String taskUuid, String status, int progress, String errorMsg) {
        taskRepository.findByTaskUuid(taskUuid).ifPresent(task -> {
            task.setStatus(status);
            task.setProgress(progress);
            if (errorMsg != null) task.setErrorMsg(errorMsg);
            taskRepository.save(task);
        });
    }

    private void updateTotalPages(String taskUuid, int totalPages) {
        taskRepository.findByTaskUuid(taskUuid).ifPresent(task -> {
            task.setTotalPages(totalPages);
            taskRepository.save(task);
        });
    }

    private void updateQuestionCount(String taskUuid, int questionCount) {
        taskRepository.findByTaskUuid(taskUuid).ifPresent(task -> {
            task.setQuestionCount(questionCount);
            taskRepository.save(task);
        });
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
