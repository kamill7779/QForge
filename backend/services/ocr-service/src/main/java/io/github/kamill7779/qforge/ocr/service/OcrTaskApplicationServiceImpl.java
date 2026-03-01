package io.github.kamill7779.qforge.ocr.service;

import io.github.kamill7779.qforge.common.contract.OcrTaskCreatedEvent;
import io.github.kamill7779.qforge.ocr.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.ocr.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.ocr.dto.OcrTaskCreateRequest;
import io.github.kamill7779.qforge.ocr.entity.OcrTask;
import io.github.kamill7779.qforge.ocr.repository.OcrTaskRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OcrTaskApplicationServiceImpl implements OcrTaskApplicationService {

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

        OcrTaskCreatedEvent event = new OcrTaskCreatedEvent(
                task.getTaskUuid(),
                task.getBizType(),
                task.getBizId(),
                task.getImageBase64(),
                task.getRequestUser(),
                Instant.now().toString()
        );
        rabbitTemplate.convertAndSend(
                RabbitTopologyConfig.OCR_EXCHANGE,
                RabbitTopologyConfig.ROUTING_TASK_CREATED,
                event
        );

        return new OcrTaskAcceptedResponse(task.getTaskUuid(), task.getStatus());
    }
}

