package io.github.kamill7779.qforge.question.mq;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.DbPersistConstants;
import io.github.kamill7779.qforge.common.contract.DbWriteBackEvent;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.common.contract.OcrTaskResultEvent;
import io.github.kamill7779.qforge.question.config.RabbitTopologyConfig;
import io.github.kamill7779.qforge.question.dto.QuestionAssetResponse;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionAsset;
import io.github.kamill7779.qforge.question.redis.TaskStateRedisService;
import io.github.kamill7779.qforge.question.repository.QuestionAssetRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import io.github.kamill7779.qforge.question.ws.OcrWsPushService;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class OcrResultConsumer {

    private static final Logger log = LoggerFactory.getLogger(OcrResultConsumer.class);

    /** Redis 图片缓存 TTL（30 秒） */
    private static final Duration ASSET_CACHE_TTL = Duration.ofSeconds(30);
    private static final String ASSET_CACHE_PREFIX = "question:assets:";

    private final OcrWsPushService ocrWsPushService;
    private final TaskStateRedisService taskStateRedisService;
    private final RabbitTemplate rabbitTemplate;
    private final QuestionRepository questionRepository;
    private final QuestionAssetRepository questionAssetRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public OcrResultConsumer(
            OcrWsPushService ocrWsPushService,
            TaskStateRedisService taskStateRedisService,
            RabbitTemplate rabbitTemplate,
            QuestionRepository questionRepository,
            QuestionAssetRepository questionAssetRepository,
            StringRedisTemplate redis,
            ObjectMapper objectMapper
    ) {
        this.ocrWsPushService = ocrWsPushService;
        this.taskStateRedisService = taskStateRedisService;
        this.rabbitTemplate = rabbitTemplate;
        this.questionRepository = questionRepository;
        this.questionAssetRepository = questionAssetRepository;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @RabbitListener(queues = RabbitTopologyConfig.OCR_RESULT_QUESTION_QUEUE)
    public void onOcrResult(OcrTaskResultEvent event) {
        log.info("Received OCR result for taskUuid={}, status={}", event.taskUuid(), event.status());

        // ---- 1. Redis 热状态更新 ----
        if ("SUCCESS".equals(event.status())) {
            taskStateRedisService.completeOcrTask(event.taskUuid(), event.recognizedText());
        } else {
            taskStateRedisService.failOcrTask(event.taskUuid(), event.errorMessage());
        }

        // ---- 2. 从 Redis 获取任务映射（替代旧的 sleep-retry DB 查询）----
        String requestUser = null;
        Optional<Map<String, Object>> redisState = taskStateRedisService.getOcrTask(event.taskUuid());
        if (redisState.isPresent()) {
            requestUser = (String) redisState.get().get("userId");
        }

        // ---- 3. 保存裁剪图片到 q_question_asset（需在 WS 推送前完成）----
        if ("SUCCESS".equals(event.status()) && event.extractedImagesJson() != null) {
            saveExtractedImages(event);
        }

        // ---- 4. 投递异步落库写回事件（MQ 队列 + 自动重试，不阻塞主流程）----
        DbWriteBackEvent writeBack = DbWriteBackEvent.ocr(
                event.taskUuid(),
                event.bizId(),
                "SUCCESS".equals(event.status()) ? "CONFIRMED" : event.status(),
                requestUser,
                event.bizType(),
                event.recognizedText(),
                event.errorMessage()
        );
        rabbitTemplate.convertAndSend(
                DbPersistConstants.DB_EXCHANGE,
                DbPersistConstants.ROUTING_DB_PERSIST,
                writeBack
        );
        log.info("Published DbWriteBackEvent for OCR taskUuid={} status={}",
                event.taskUuid(), writeBack.status());

        // ---- 5. WebSocket 推送 ----
        if (requestUser == null) {
            log.error("Cannot determine requestUser for taskUuid={}, skipping WS push", event.taskUuid());
            return;
        }

        if ("SUCCESS".equals(event.status())) {
            ocrWsPushService.push(requestUser, "ocr.task.succeeded", Map.of(
                    "taskUuid", event.taskUuid(),
                    "bizType", event.bizType(),
                    "bizId", event.bizId(),
                    "recognizedText", event.recognizedText() == null ? "" : event.recognizedText()
            ));
        } else if ("FAILED".equals(event.status())) {
            ocrWsPushService.push(requestUser, "ocr.task.failed", Map.of(
                    "taskUuid", event.taskUuid(),
                    "bizType", event.bizType(),
                    "bizId", event.bizId(),
                    "errorMessage", event.errorMessage() == null ? "Unknown error" : event.errorMessage()
            ));
        }
    }

    /**
     * 解析 extractedImagesJson，将裁剪的图片保存到 q_question_asset 并缓存到 Redis。
     */
    private void saveExtractedImages(OcrTaskResultEvent event) {
        List<ExtractedImage> images;
        try {
            images = objectMapper.readValue(
                    event.extractedImagesJson(), new TypeReference<List<ExtractedImage>>() {});
        } catch (Exception ex) {
            log.warn("Failed to parse extractedImagesJson for task {}: {}",
                    event.taskUuid(), ex.getMessage());
            return;
        }

        if (images == null || images.isEmpty()) {
            return;
        }

        // 使用 event.requestUser() 解析 questionUuid → questionId
        String ownerUser = event.requestUser();
        if (ownerUser == null || ownerUser.isBlank()) {
            log.warn("requestUser is empty in OcrTaskResultEvent for task {}, " +
                    "cannot save extracted images", event.taskUuid());
            return;
        }

        Optional<Question> questionOpt = questionRepository
                .findByQuestionUuidAndOwnerUser(event.bizId(), ownerUser);
        if (questionOpt.isEmpty()) {
            log.warn("Question not found for bizId={} ownerUser={}, " +
                    "cannot save extracted images", event.bizId(), ownerUser);
            return;
        }
        Long questionId = questionOpt.get().getId();

        List<QuestionAssetResponse> cachedAssets = new ArrayList<>();
        for (ExtractedImage img : images) {
            try {
                // Upsert: 如果已存在相同 refKey 则更新，否则新建
                Optional<QuestionAsset> existing = questionAssetRepository
                        .findByQuestionIdAndRefKey(questionId, img.refKey());
                QuestionAsset asset;
                if (existing.isPresent()) {
                    asset = existing.get();
                    asset.setImageData(img.imageBase64());
                    asset.setMimeType(img.mimeType());
                    asset.setUpdatedAt(LocalDateTime.now());
                } else {
                    asset = new QuestionAsset();
                    asset.setAssetUuid(UUID.randomUUID().toString());
                    asset.setQuestionId(questionId);
                    asset.setAssetType("INLINE_IMAGE");
                    asset.setRefKey(img.refKey());
                    asset.setImageData(img.imageBase64());
                    asset.setMimeType(img.mimeType());
                    asset.setDeleted(false);
                    asset.setCreatedAt(LocalDateTime.now());
                    asset.setUpdatedAt(LocalDateTime.now());
                }
                questionAssetRepository.save(asset);
                cachedAssets.add(new QuestionAssetResponse(
                        asset.getAssetUuid(), asset.getRefKey(),
                        asset.getImageData(), asset.getMimeType()));
                log.info("Saved image asset {} for question {} (refKey={})",
                        asset.getAssetUuid(), event.bizId(), img.refKey());
            } catch (Exception ex) {
                log.warn("Failed to save image asset refKey={} for question {}: {}",
                        img.refKey(), event.bizId(), ex.getMessage());
            }
        }

        // Redis 缓存（30s TTL）—— 供前端 GET /assets 快速读取
        if (!cachedAssets.isEmpty()) {
            try {
                String cacheKey = ASSET_CACHE_PREFIX + event.bizId();
                String cacheValue = objectMapper.writeValueAsString(cachedAssets);
                redis.opsForValue().set(cacheKey, cacheValue, ASSET_CACHE_TTL);
                log.info("Cached {} image assets in Redis for question {} (TTL={}s)",
                        cachedAssets.size(), event.bizId(), ASSET_CACHE_TTL.getSeconds());
            } catch (Exception ex) {
                log.warn("Failed to cache image assets in Redis for question {}: {}",
                        event.bizId(), ex.getMessage());
            }
        }
    }
}
