package io.github.kamill7779.qforge.examparse.service;

import io.github.kamill7779.qforge.common.contract.ExamParseTaskCreatedEvent;
import io.github.kamill7779.qforge.examparse.config.QForgeBusinessProperties;
import io.github.kamill7779.qforge.examparse.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.examparse.entity.ExamParseQuestion;
import io.github.kamill7779.qforge.examparse.entity.ExamParseSourceFile;
import io.github.kamill7779.qforge.examparse.entity.ExamParseTask;
import io.github.kamill7779.qforge.examparse.repository.ExamParseQuestionRepository;
import io.github.kamill7779.qforge.examparse.repository.ExamParseSourceFileRepository;
import io.github.kamill7779.qforge.examparse.repository.ExamParseTaskRepository;
import io.github.kamill7779.qforge.storage.QForgeStorageService;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * 试卷解析任务提交服务。
 */
@Service
public class ExamParseCommandService {

    private static final Logger log = LoggerFactory.getLogger(ExamParseCommandService.class);

    private final ExamParseTaskRepository taskRepository;
    private final ExamParseSourceFileRepository sourceFileRepository;
    private final ExamParseQuestionRepository questionRepository;
    private final RabbitTemplate rabbitTemplate;
    private final QForgeBusinessProperties bizProps;
    private final QForgeStorageService storageService;

    public ExamParseCommandService(ExamParseTaskRepository taskRepository,
                                    ExamParseSourceFileRepository sourceFileRepository,
                                    ExamParseQuestionRepository questionRepository,
                                    RabbitTemplate rabbitTemplate,
                                    QForgeBusinessProperties bizProps,
                                    QForgeStorageService storageService) {
        this.taskRepository = taskRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.questionRepository = questionRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.bizProps = bizProps;
        this.storageService = storageService;
    }

    /**
     * 创建解析任务：校验文件、入库、发布 MQ。
     */
    @Transactional
    public ExamParseTask createTask(MultipartFile[] files, boolean hasAnswerHint, String ownerUser) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("至少上传 1 个文件");
        }
        int maxFiles = bizProps.getMaxExamUploadFiles();
        if (files.length > maxFiles) {
            throw new IllegalArgumentException("最多上传 " + maxFiles + " 个文件");
        }

        String taskUuid = UUID.randomUUID().toString();

        ExamParseTask task = new ExamParseTask();
        task.setTaskUuid(taskUuid);
        task.setOwnerUser(ownerUser);
        task.setStatus("PENDING");
        task.setProgress(0);
        task.setFileCount(files.length);
        task.setTotalPages(0);
        task.setQuestionCount(0);
        task.setHasAnswerHint(hasAnswerHint);
        taskRepository.save(task);

        List<ExamParseTaskCreatedEvent.SourceFileMeta> fileMetas = new ArrayList<>();

        for (int i = 0; i < files.length; i++) {
            MultipartFile file = files[i];
            String originalName = file.getOriginalFilename();
            String ext = extractExtension(originalName);

            Set<String> allowedExtensions = bizProps.getAllowedExamExtensionSet();
            if (!allowedExtensions.contains(ext.toLowerCase())) {
                throw new IllegalArgumentException("不支持的文件类型: " + ext
                        + " (允许: " + allowedExtensions + ")");
            }

            String fileType = "pdf".equalsIgnoreCase(ext) ? "PDF" : "IMAGE";
            int pageCount = "PDF".equals(fileType) ? 0 : 1;

            try {
                byte[] fileBytes = file.getBytes();
                String objectKey = storageService.buildObjectKey(
                        "exam-parse/source",
                        taskUuid,
                        i + "-" + (originalName != null ? originalName : ("file-" + i))
                );
                String storageRef = storageService.putObject(
                        objectKey,
                        new java.io.ByteArrayInputStream(fileBytes),
                        fileBytes.length,
                        file.getContentType()
                );

                ExamParseSourceFile sf = new ExamParseSourceFile();
                sf.setTaskUuid(taskUuid);
                sf.setFileIndex(i);
                sf.setFileName(originalName != null ? originalName : "file-" + i);
                sf.setFileType(fileType);
                sf.setPageCount(pageCount);
                sf.setFileData(null);
                sf.setStorageRef(storageRef);
                sf.setBlobKey(objectKey);
                sf.setBlobSize((long) fileBytes.length);
                sf.setChecksumSha256(calculateSha256(fileBytes));
                sf.setOcrStatus("PENDING");
                sourceFileRepository.save(sf);

                fileMetas.add(new ExamParseTaskCreatedEvent.SourceFileMeta(
                        i, sf.getFileName(), fileType, pageCount));
            } catch (Exception ex) {
                throw new RuntimeException("文件处理失败: " + originalName, ex);
            }
        }

        ExamParseTaskCreatedEvent event = new ExamParseTaskCreatedEvent(
                taskUuid, ownerUser, hasAnswerHint, fileMetas);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                        RabbitTopologyConfig.EXAM_EXCHANGE,
                        RabbitTopologyConfig.ROUTING_EXAM_PARSE_TASK_CREATED,
                        event);
                log.info("Exam parse task published after commit: taskUuid={}, files={}",
                        taskUuid, files.length);
            }
        });

        log.info("Exam parse task created: taskUuid={}, files={} (MQ will publish after commit)",
                taskUuid, files.length);
        return task;
    }

    public List<ExamParseTask> listTasks(String ownerUser) {
        return taskRepository.findAllByOwnerUser(ownerUser);
    }

    public ExamParseTask getTask(String taskUuid, String ownerUser) {
        return taskRepository.findByTaskUuidAndOwnerUser(taskUuid, ownerUser)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在或无权访问: " + taskUuid));
    }

    public List<ExamParseQuestion> listQuestions(String taskUuid) {
        return questionRepository.findByTaskUuidOrderBySeqNo(taskUuid);
    }

    public ExamParseQuestion updateQuestion(String taskUuid, int seqNo, Map<String, String> updates) {
        ExamParseQuestion q = questionRepository.findByTaskUuidAndSeqNo(taskUuid, seqNo)
                .orElseThrow(() -> new IllegalArgumentException(
                        "题目不存在: taskUuid=" + taskUuid + ", seqNo=" + seqNo));

        if (updates.containsKey("stemXml")) {
            q.setStemXml(updates.get("stemXml"));
        }
        if (updates.containsKey("answerXml")) {
            q.setAnswerXml(updates.get("answerXml"));
        }
        if (updates.containsKey("questionType")) {
            q.setQuestionType(updates.get("questionType"));
        }
        if (updates.containsKey("mainTagsJson")) {
            q.setMainTagsJson(updates.get("mainTagsJson"));
        }
        if (updates.containsKey("secondaryTagsJson")) {
            q.setSecondaryTagsJson(updates.get("secondaryTagsJson"));
        }
        if (updates.containsKey("difficulty")) {
            String dv = updates.get("difficulty");
            q.setDifficulty(dv != null && !dv.isBlank() ? new java.math.BigDecimal(dv) : null);
        }

        questionRepository.save(q);
        return q;
    }

    @Transactional
    public void deleteTask(String taskUuid, String ownerUser) {
        getTask(taskUuid, ownerUser);
        taskRepository.markCancelled(taskUuid, ownerUser);
        log.info("Exam parse task cancelled: taskUuid={}", taskUuid);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    private String calculateSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Failed to calculate file checksum", ex);
        }
    }
}
