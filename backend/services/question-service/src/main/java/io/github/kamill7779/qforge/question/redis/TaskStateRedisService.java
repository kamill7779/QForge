package io.github.kamill7779.qforge.question.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
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
 * 默认 TTL 30 分钟，到期后自动淘汰（DB 中仍保留持久数据）。
 */
@Service
public class TaskStateRedisService {

    private static final Logger log = LoggerFactory.getLogger(TaskStateRedisService.class);

    private static final String AI_PREFIX  = "ai:task:";
    private static final String OCR_PREFIX = "ocr:task:";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(30);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public TaskStateRedisService(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
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
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), DEFAULT_TTL);
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
            redis.opsForValue().set(key, objectMapper.writeValueAsString(merged), DEFAULT_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize merged Redis value for key={}: {}", key, e.getMessage());
        }
    }
}
