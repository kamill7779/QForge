package io.github.kamill7779.qforge.question.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.common.contract.ExtractedImage;
import io.github.kamill7779.qforge.question.config.QForgeBusinessProperties;
import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis 任务热状态服务。
 * <p>
 * 所有通过 MQ 异步处理的 AI / OCR 任务，在创建时写入 Redis 作为权威热状态，
 * 消费者收到结果后先更新 Redis，再异步落库。
 * <ul>
 *     <li>AI:  key = {@code ai:task:{taskUuid}}</li>
 *     <li>OCR: key = {@code ocr:task:{taskUuid}}</li>
 * </ul>
 * TTL 可通过 Nacos 的 {@code qforge.business.*} 配置热调整。
 */
@Service
public class TaskStateRedisService {

    private static final Logger log = LoggerFactory.getLogger(TaskStateRedisService.class);

    private static final String AI_PREFIX  = "ai:task:";
    private static final String OCR_PREFIX = "ocr:task:";
    private static final String ANSWER_OCR_GUARD_PREFIX = "ocr:answer:guard:";
    private static final String ANSWER_OCR_ASSET_PREFIX = "ocr:answer:assets:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final QForgeBusinessProperties bizProps;

    public TaskStateRedisService(StringRedisTemplate redis, ObjectMapper objectMapper,
                                  QForgeBusinessProperties bizProps) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.bizProps = bizProps;
    }

    // ======================== AI task ========================

    /**
     * 创建 AI 任务时写入 Redis。
     */
    public void createAiTask(String taskUuid, String questionUuid, String userId) {
        Map<String, Object> state = Map.of(
                "taskUuid", taskUuid,
                "questionUuid", questionUuid,
                "userId", userId,
                "status", "PENDING"
        );
        set(AI_PREFIX + taskUuid, state);
    }

    /**
     * AI 任务成功时更新 Redis。
     */
    public void completeAiTask(String taskUuid, String suggestedTags,
                                Object suggestedDifficulty, String reasoning) {
        Map<String, Object> patch = Map.of(
                "status", "SUCCESS",
                "suggestedTags", suggestedTags == null ? "" : suggestedTags,
                "suggestedDifficulty", suggestedDifficulty == null ? "" : suggestedDifficulty,
                "reasoning", reasoning == null ? "" : reasoning
        );
        merge(AI_PREFIX + taskUuid, patch);
    }

    /**
     * AI 任务失败时更新 Redis。
     */
    public void failAiTask(String taskUuid, String errorMsg) {
        Map<String, Object> patch = Map.of(
                "status", "FAILED",
                "errorMsg", errorMsg == null ? "Unknown error" : errorMsg
        );
        merge(AI_PREFIX + taskUuid, patch);
    }

    /**
     * 读取 AI 任务热状态。
     */
    public Optional<Map<String, Object>> getAiTask(String taskUuid) {
        return get(AI_PREFIX + taskUuid);
    }

    // ======================== OCR task =======================

    /**
     * 创建 OCR 任务时写入 Redis（解决消费者在 DB 行可见之前收到 MQ 消息的竞态条件）。
     */
    public void createOcrTask(String taskUuid, String questionUuid, String bizType, String userId) {
        Map<String, Object> state = Map.of(
                "taskUuid", taskUuid,
                "questionUuid", questionUuid,
                "bizType", bizType,
                "userId", userId,
                "status", "PENDING"
        );
        set(OCR_PREFIX + taskUuid, state);
    }

    /**
     * OCR 任务成功时更新 Redis。
     */
    public void completeOcrTask(String taskUuid, String recognizedText) {
        Map<String, Object> patch = Map.of(
                "status", "CONFIRMED",
                "recognizedText", recognizedText == null ? "" : recognizedText
        );
        merge(OCR_PREFIX + taskUuid, patch);
    }

    /**
     * OCR 任务失败时更新 Redis。
     */
    public void failOcrTask(String taskUuid, String errorMsg) {
        Map<String, Object> patch = Map.of(
                "status", "FAILED",
                "errorMsg", errorMsg == null ? "Unknown error" : errorMsg
        );
        merge(OCR_PREFIX + taskUuid, patch);
    }

    /**
     * 读取 OCR 任务热状态。
     */
    public Optional<Map<String, Object>> getOcrTask(String taskUuid) {
        return get(OCR_PREFIX + taskUuid);
    }

    /**
     * 尝试获取单题答案 OCR 并发保护键。
     * <p>
     * key = {@code ocr:answer:guard:{questionUuid}}，值记录 holder（用户/时间）仅用于排查。
     *
     * @return true 表示获取成功；false 表示已有进行中的答案 OCR 任务
     */
    public boolean tryAcquireAnswerOcrGuard(String questionUuid, String holder) {
        String key = ANSWER_OCR_GUARD_PREFIX + questionUuid;
        String value = (holder == null || holder.isBlank() ? "unknown" : holder) + ":" + System.currentTimeMillis();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, value, Duration.ofMinutes(bizProps.getAnswerOcrGuardTtlMinutes()));
        return Boolean.TRUE.equals(acquired);
    }

    /**
     * 释放单题答案 OCR 并发保护键。
     */
    public void releaseAnswerOcrGuard(String questionUuid) {
        redis.delete(ANSWER_OCR_GUARD_PREFIX + questionUuid);
    }

    /**
     * Cache unsaved answer OCR cropped images in Redis, keyed by questionUuid.
     */
    public void saveAnswerOcrAssets(String questionUuid, java.util.List<ExtractedImage> images) {
        if (questionUuid == null || questionUuid.isBlank() || images == null || images.isEmpty()) {
            return;
        }
        String key = ANSWER_OCR_ASSET_PREFIX + questionUuid;
        Map<String, String> merged = new HashMap<>(getAnswerOcrAssets(questionUuid));
        for (ExtractedImage image : images) {
            if (image == null || image.refKey() == null || image.refKey().isBlank()) {
                continue;
            }
            if (image.imageBase64() != null && !image.imageBase64().isBlank()) {
                merged.put(image.refKey(), image.imageBase64());
            }
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(merged), Duration.ofHours(bizProps.getAnswerOcrAssetTtlHours()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache answer OCR assets for question {}: {}", questionUuid, e.getMessage());
        }
    }

    /**
     * Read cached answer OCR images (refKey -> imageBase64).
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> getAnswerOcrAssets(String questionUuid) {
        if (questionUuid == null || questionUuid.isBlank()) {
            return Map.of();
        }
        String key = ANSWER_OCR_ASSET_PREFIX + questionUuid;
        String json = redis.opsForValue().get(key);
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            Map<String, Object> raw = objectMapper.readValue(json, Map.class);
            Map<String, String> out = new HashMap<>();
            raw.forEach((k, v) -> {
                if (k != null && v != null) {
                    out.put(k, String.valueOf(v));
                }
            });
            return out;
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize answer OCR assets for question {}: {}", questionUuid, e.getMessage());
            return Map.of();
        }
    }

    /**
     * Remove cached refs after they are persisted into answer assets table.
     */
    public void removeAnswerOcrAssets(String questionUuid, Collection<String> refs) {
        if (questionUuid == null || questionUuid.isBlank() || refs == null || refs.isEmpty()) {
            return;
        }
        Map<String, String> existing = new HashMap<>(getAnswerOcrAssets(questionUuid));
        if (existing.isEmpty()) {
            return;
        }
        for (String ref : refs) {
            if (ref != null && !ref.isBlank()) {
                existing.remove(ref);
            }
        }
        String key = ANSWER_OCR_ASSET_PREFIX + questionUuid;
        if (existing.isEmpty()) {
            redis.delete(key);
            return;
        }
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(existing), Duration.ofHours(bizProps.getAnswerOcrAssetTtlHours()));
        } catch (JsonProcessingException e) {
            log.warn("Failed to update answer OCR assets cache for question {}: {}", questionUuid, e.getMessage());
        }
    }

    // ======================== internal =======================

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> get(String key) {
        String json = redis.opsForValue().get(key);
        if (json == null) return Optional.empty();
        try {
            return Optional.of(objectMapper.readValue(json, Map.class));
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize Redis key={}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    private void set(String key, Map<String, Object> value) {
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), Duration.ofMinutes(bizProps.getTaskStateTtlMinutes()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize Redis value for key={}: {}", key, e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void merge(String key, Map<String, Object> patch) {
        String existing = redis.opsForValue().get(key);
        Map<String, Object> merged;
        if (existing != null) {
            try {
                merged = new java.util.HashMap<>(objectMapper.readValue(existing, Map.class));
            } catch (JsonProcessingException e) {
                merged = new java.util.HashMap<>();
            }
        } else {
            merged = new java.util.HashMap<>();
        }
        merged.putAll(patch);
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(merged), Duration.ofMinutes(bizProps.getTaskStateTtlMinutes()));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize merged Redis value for key={}: {}", key, e.getMessage());
        }
    }
}
