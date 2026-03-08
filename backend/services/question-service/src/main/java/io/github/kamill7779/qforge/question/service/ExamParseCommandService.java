package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.common.contract.ExamParseTaskCreatedEvent;
import io.github.kamill7779.qforge.question.config.QForgeBusinessProperties;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.entity.ExamParseQuestion;
import io.github.kamill7779.qforge.question.entity.ExamParseSourceFile;
import io.github.kamill7779.qforge.question.entity.ExamParseTask;
import io.github.kamill7779.qforge.question.repository.ExamParseQuestionRepository;
import io.github.kamill7779.qforge.question.repository.ExamParseSourceFileRepository;
import io.github.kamill7779.qforge.question.repository.ExamParseTaskRepository;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
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

    public ExamParseCommandService(ExamParseTaskRepository taskRepository,
                                    ExamParseSourceFileRepository sourceFileRepository,
                                    ExamParseQuestionRepository questionRepository,
                                    RabbitTemplate rabbitTemplate,
                                    QForgeBusinessProperties bizProps) {
        this.taskRepository = taskRepository;
        this.sourceFileRepository = sourceFileRepository;
        this.questionRepository = questionRepository;
        this.rabbitTemplate = rabbitTemplate;
        this.bizProps = bizProps;
    }

    /**
     * 创建解析任务：校验文件、入库、发布 MQ。
     */
    @Transactional
    public ExamParseTask createTask(MultipartFile[] files, boolean hasAnswerHint, String ownerUser) {
        // 文件校验
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("至少上传 1 个文件");
        }
        int maxFiles = bizProps.getMaxExamUploadFiles();
        if (files.length > maxFiles) {
            throw new IllegalArgumentException("最多上传 " + maxFiles + " 个文件");
        }

        String taskUuid = UUID.randomUUID().toString();

        // 创建任务主记录
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

        // 存储源文件 + 构建 MQ 事件
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
            int pageCount = "PDF".equals(fileType) ? 0 : 1; // PDF 页数由 ocr-service 解析后更新

            try {
                byte[] fileBytes = file.getBytes();
                String base64Data = Base64.getEncoder().encodeToString(fileBytes);

                ExamParseSourceFile sf = new ExamParseSourceFile();
                sf.setTaskUuid(taskUuid);
                sf.setFileIndex(i);
                sf.setFileName(originalName != null ? originalName : "file-" + i);
                sf.setFileType(fileType);
                sf.setPageCount(pageCount);
                sf.setFileData(base64Data);
                sf.setOcrStatus("PENDING");
                sourceFileRepository.save(sf);

                fileMetas.add(new ExamParseTaskCreatedEvent.SourceFileMeta(
                        i, sf.getFileName(), fileType, pageCount));
            } catch (Exception ex) {
                throw new RuntimeException("文件处理失败: " + originalName, ex);
            }
        }

        // 发布 MQ 事件（仅携带元数据，不含 base64）——延迟到事务提交后发送，
        // 避免 ocr-service 消费者在事务提交前查 DB 读不到源文件。
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
        ExamParseTask task = getTask(taskUuid, ownerUser);
        // 删除关联数据
        questionRepository.delete(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ExamParseQuestion>lambdaQuery()
                        .eq(ExamParseQuestion::getTaskUuid, taskUuid));
        sourceFileRepository.delete(
                com.baomidou.mybatisplus.core.toolkit.Wrappers.<ExamParseSourceFile>lambdaQuery()
                        .eq(ExamParseSourceFile::getTaskUuid, taskUuid));
        taskRepository.deleteById(task.getId());
        log.info("Exam parse task deleted: taskUuid={}", taskUuid);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }
}
