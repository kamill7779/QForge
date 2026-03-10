package io.github.kamill7779.qforge.exam.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.exam.config.QForgeCacheProperties;
import io.github.kamill7779.qforge.exam.dto.BasketItemResponse;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ExamCacheService {

    private static final String BASKET_UUID_CACHE_PREFIX = "qforge:basket:uuids:";
    private static final String BASKET_ITEM_CACHE_PREFIX = "qforge:basket:items:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final QForgeCacheProperties cacheProperties;

    public ExamCacheService(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            QForgeCacheProperties cacheProperties
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    public List<String> getBasketUuids(String requestUser, Supplier<List<String>> loader) {
        return getOrLoad(
                BASKET_UUID_CACHE_PREFIX + requestUser,
                new TypeReference<List<String>>() {},
                Duration.ofSeconds(cacheProperties.getBasketTtlSeconds()),
                loader
        );
    }

    public List<BasketItemResponse> getBasketItems(String requestUser, Supplier<List<BasketItemResponse>> loader) {
        return getOrLoad(
                BASKET_ITEM_CACHE_PREFIX + requestUser,
                new TypeReference<List<BasketItemResponse>>() {},
                Duration.ofSeconds(cacheProperties.getBasketTtlSeconds()),
                loader
        );
    }

    public void evictBasket(String requestUser) {
        redis.delete(List.of(
                BASKET_UUID_CACHE_PREFIX + requestUser,
                BASKET_ITEM_CACHE_PREFIX + requestUser
        ));
    }

    private <T> T getOrLoad(String key, TypeReference<T> typeReference, Duration ttl, Supplier<T> loader) {
        try {
            String cached = redis.opsForValue().get(key);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, typeReference);
            }
        } catch (Exception ignored) {
            // Fall through to the loader on cache miss or decode failure.
        }

        T loaded = loader.get();
        try {
            redis.opsForValue().set(key, objectMapper.writeValueAsString(loaded), ttl);
        } catch (Exception ignored) {
            // Ignore cache write failures.
        }
        return loaded;
    }
}
