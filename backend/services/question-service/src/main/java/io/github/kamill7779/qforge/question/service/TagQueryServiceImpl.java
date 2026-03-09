package io.github.kamill7779.qforge.question.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.question.config.QForgeCacheProperties;
import io.github.kamill7779.qforge.question.dto.MainTagCategoryResponse;
import io.github.kamill7779.qforge.question.dto.TagCatalogResponse;
import io.github.kamill7779.qforge.question.dto.TagOptionResponse;
import io.github.kamill7779.qforge.question.entity.Tag;
import io.github.kamill7779.qforge.question.entity.TagCategory;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TagQueryServiceImpl implements TagQueryService {

    private static final String TAG_CATALOG_CACHE_KEY = "qforge:tag-catalog:v1";

    private final TagRepository tagRepository;
    private final TagCategoryRepository tagCategoryRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final QForgeCacheProperties cacheProperties;

    public TagQueryServiceImpl(
            TagRepository tagRepository,
            TagCategoryRepository tagCategoryRepository,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            QForgeCacheProperties cacheProperties
    ) {
        this.tagRepository = tagRepository;
        this.tagCategoryRepository = tagCategoryRepository;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    @Override
    public TagCatalogResponse getMainTagCatalog() {
        TagCatalogResponse cached = readCachedCatalog();
        if (cached != null) {
            return cached;
        }

        List<TagCategory> allCategories = tagCategoryRepository.findEnabledCategories();
        List<TagCategory> mainCategories = tagCategoryRepository.findEnabledMainCategories();
        List<String> mainCategoryCodes = mainCategories.stream().map(TagCategory::getCategoryCode).toList();
        Map<String, List<TagOptionResponse>> optionsByCategory = tagRepository.findSystemByCategoryCodes(mainCategoryCodes)
                .stream()
                .collect(Collectors.groupingBy(
                        Tag::getCategoryCode,
                        Collectors.mapping(tag -> new TagOptionResponse(tag.getTagCode(), tag.getTagName()), Collectors.toList())
                ));

        List<MainTagCategoryResponse> mainCategoryResponses = mainCategories.stream()
                .map(category -> new MainTagCategoryResponse(
                        category.getCategoryCode(),
                        category.getCategoryName(),
                        optionsByCategory.getOrDefault(category.getCategoryCode(), List.of())
                ))
                .toList();

        TagCategory secondaryCategory = allCategories.stream()
                .filter(category -> "SECONDARY".equals(category.getCategoryKind()))
                .findFirst()
                .orElse(null);

        TagCatalogResponse response = new TagCatalogResponse(
                mainCategoryResponses,
                secondaryCategory == null ? "SECONDARY_CUSTOM" : secondaryCategory.getCategoryCode(),
                secondaryCategory == null ? "Secondary" : secondaryCategory.getCategoryName()
        );
        writeCachedCatalog(response);
        return response;
    }

    private TagCatalogResponse readCachedCatalog() {
        try {
            String cached = redis.opsForValue().get(TAG_CATALOG_CACHE_KEY);
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cached, TagCatalogResponse.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeCachedCatalog(TagCatalogResponse response) {
        try {
            Duration ttl = Duration.ofSeconds(cacheProperties.getTagCatalogTtlSeconds());
            redis.opsForValue().set(TAG_CATALOG_CACHE_KEY, objectMapper.writeValueAsString(response), ttl);
        } catch (Exception ignored) {
            // Cache failure must not block catalog queries.
        }
    }
}
