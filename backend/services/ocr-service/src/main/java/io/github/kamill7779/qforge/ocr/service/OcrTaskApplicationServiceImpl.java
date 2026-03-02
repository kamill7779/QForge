package io.github.kamill7779.qforge.ocr.service;

import io.github.kamill7779.qforge.common.contract.OcrTaskCreatedEvent;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.ocr.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.ocr.dto.OcrTaskCreateRequest;
import io.github.kamill7779.qforge.ocr.entity.OcrTask;
import io.github.kamill7779.qforge.ocr.repository.OcrTaskRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class OcrTaskApplicationServiceImpl implements OcrTaskApplicationService {

    private static final Logger log = LoggerFactory.getLogger(OcrTaskApplicationServiceImpl.class);

    private final OcrTaskRepository ocrTaskRepository;
    private final RabbitTemplate rabbitTemplate;

    public OcrTaskApplicationServiceImpl(OcrTaskRepository ocrTaskRepository, RabbitTemplate rabbitTemplate) {
        this.ocrTaskRepository = ocrTaskRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    @Transactional
    public OcrTaskAcceptedResponse createTask(OcrTaskCreateRequest request) {
        OcrTask task = new OcrTask();
        task.setTaskUuid(UUID.randomUUID().toString());
        task.setBizType(request.getBizType());
        task.setBizId(request.getBizId());
        task.setImageBase64(request.getImageBase64());
        task.setRequestUser(request.getRequestUser());
        task.setStatus("PENDING");
        task.setProvider("GLM_OCR");
        ocrTaskRepository.save(task);
        log.info("OCR task created: taskUuid={}, bizType={}, bizId={}",
                task.getTaskUuid(), task.getBizType(), task.getBizId());

        OcrTaskCreatedEvent event = new OcrTaskCreatedEvent(
                task.getTaskUuid(),
                task.getBizType(),
                task.getBizId(),
                task.getImageBase64(),
                task.getRequestUser(),
                Instant.now().toString()
        );

        // Publish MQ message AFTER the transaction commits to avoid race condition
        // where consumer reads the message before DB row is visible.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                rabbitTemplate.convertAndSend(
                        RabbitTopologyConfig.OCR_EXCHANGE,
                        RabbitTopologyConfig.ROUTING_TASK_CREATED,
                        event
                );
                log.debug("OCR task MQ event published after commit: {}", task.getTaskUuid());
            }
        });

        return new OcrTaskAcceptedResponse(task.getTaskUuid(), task.getStatus());
    }
}

