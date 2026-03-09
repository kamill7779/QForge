package io.github.kamill7779.qforge.question.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionTagRel;
import io.github.kamill7779.qforge.question.entity.Tag;
import io.github.kamill7779.qforge.question.entity.TagCategory;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.repository.QuestionTagRelRepository;
import io.github.kamill7779.qforge.question.repository.TagCategoryRepository;
import io.github.kamill7779.qforge.question.repository.TagRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class QuestionTagAssignmentService {

    private static final String CATEGORY_SECONDARY_CUSTOM = "SECONDARY_CUSTOM";
    private static final String TAG_SCOPE_USER = "USER";
    private static final String TAG_CODE_UNCATEGORIZED = "UNCATEGORIZED";
    private static final int MAX_SECONDARY_TAG_LENGTH = 64;

    private final TagRepository tagRepository;
    private final TagCategoryRepository tagCategoryRepository;
    private final QuestionTagRelRepository questionTagRelRepository;
    private final ObjectMapper objectMapper;

    public QuestionTagAssignmentService(
            TagRepository tagRepository,
            TagCategoryRepository tagCategoryRepository,
            QuestionTagRelRepository questionTagRelRepository,
            ObjectMapper objectMapper
    ) {
        this.tagRepository = tagRepository;
        this.tagCategoryRepository = tagCategoryRepository;
        this.questionTagRelRepository = questionTagRelRepository;
        this.objectMapper = objectMapper;
    }

    public void replaceTags(Question question, List<String> tags, String requestUser) {
        questionTagRelRepository.deleteByQuestionId(question.getId());

        Set<Long> insertedTagIds = new LinkedHashSet<>();
        Set<String> assignedMainCategories = new LinkedHashSet<>();
        TagCategory secondaryCategory = tagCategoryRepository.findEnabledByCode(CATEGORY_SECONDARY_CUSTOM)
                .orElse(null);

        for (String tagText : tags == null ? List.<String>of() : tags) {
            String normalized = tagText == null ? "" : tagText.trim();
            if (normalized.isEmpty()) {
                continue;
            }

            Optional<Tag> systemTag = tagRepository.findSystemTagByCode(normalized)
                    .or(() -> tagRepository.findSystemTagByName(normalized));
            if (systemTag.isPresent()) {
                Tag tag = systemTag.get();
                if (insertedTagIds.add(tag.getId())) {
                    saveQuestionTagRel(question.getId(), tag.getId(), tag.getCategoryCode(), requestUser);
                    assignedMainCategories.add(tag.getCategoryCode());
                }
                continue;
            }

            if (secondaryCategory == null) {
                continue;
            }

            if (normalized.length() > MAX_SECONDARY_TAG_LENGTH) {
                throw new BusinessValidationException(
                        "QUESTION_SECONDARY_TAG_TOO_LONG",
                        "Secondary tag length must be <= 64",
                        Map.of("tag", normalized)
                );
            }

            String normalizedCode = toCustomTagCode(normalized);
            Tag tag = tagRepository.findUserTagByCategoryAndCode(requestUser, CATEGORY_SECONDARY_CUSTOM, normalizedCode)
                    .orElseGet(() -> createUserCustomTag(requestUser, normalized));
            if (insertedTagIds.add(tag.getId())) {
                saveQuestionTagRel(question.getId(), tag.getId(), CATEGORY_SECONDARY_CUSTOM, requestUser);
            }
        }

        applyMissingDefaultMainTags(question.getId(), requestUser, assignedMainCategories);
    }

    public void applyFromParsePayload(Question question, String mainTagsJson, String secondaryTagsJson, String requestUser) {
        List<String> mergedTags = new ArrayList<>();
        mergedTags.addAll(parseFlexibleTagTokens(mainTagsJson));
        mergedTags.addAll(parseFlexibleTagTokens(secondaryTagsJson));
        replaceTags(question, mergedTags, requestUser);
    }

    private void applyMissingDefaultMainTags(Long questionId, String requestUser, Set<String> assignedMainCategories) {
        List<TagCategory> mainCategories = tagCategoryRepository.findEnabledMainCategories();
        for (TagCategory category : mainCategories) {
            if (assignedMainCategories.contains(category.getCategoryCode())) {
                continue;
            }
            Tag resolvedTag = tagRepository.findSystemTagByCategoryAndCode(category.getCategoryCode(), TAG_CODE_UNCATEGORIZED)
                    .orElseGet(() -> {
                        List<Tag> options = tagRepository.findSystemTagsByCategory(category.getCategoryCode());
                        return options.isEmpty() ? null : options.get(0);
                    });
            if (resolvedTag != null) {
                saveQuestionTagRel(questionId, resolvedTag.getId(), category.getCategoryCode(), requestUser);
            }
        }
    }

    private List<String> parseFlexibleTagTokens(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return List.of();
        }

        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        try {
            JsonNode node = objectMapper.readTree(rawJson);
            collectTokens(node, tokens);
        } catch (Exception ignored) {
            addPlainTextTokens(rawJson, tokens);
        }
        return List.copyOf(tokens);
    }

    private void collectTokens(JsonNode node, Set<String> tokens) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isArray()) {
            for (JsonNode child : node) {
                collectTokens(child, tokens);
            }
            return;
        }
        if (node.isTextual()) {
            addPlainTextTokens(node.asText(), tokens);
            return;
        }
        if (!node.isObject()) {
            return;
        }

        String candidate = firstPresentText(node, "tagCode", "code", "value", "tagName", "name", "label", "text");
        if (candidate != null) {
            addPlainTextTokens(candidate, tokens);
        }
    }

    private String firstPresentText(JsonNode node, String... fieldNames) {
        for (String fieldName : fieldNames) {
            JsonNode field = node.get(fieldName);
            if (field != null && field.isTextual() && !field.asText().isBlank()) {
                return field.asText();
            }
        }
        return null;
    }

    private void addPlainTextTokens(String raw, Set<String> tokens) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String token : raw.split("[,，\\s]+")) {
            String normalized = token == null ? "" : token.trim();
            if (!normalized.isEmpty()) {
                tokens.add(normalized);
            }
        }
    }

    private String toCustomTagCode(String tagText) {
        return tagText.trim().toLowerCase(Locale.ROOT);
    }

    private Tag createUserCustomTag(String requestUser, String tagText) {
        Tag tag = new Tag();
        tag.setTagUuid(UUID.randomUUID().toString());
        tag.setCategoryCode(CATEGORY_SECONDARY_CUSTOM);
        tag.setTagCode(toCustomTagCode(tagText));
        tag.setTagName(tagText.trim());
        tag.setScope(TAG_SCOPE_USER);
        tag.setOwnerUser(requestUser);
        return tagRepository.save(tag);
    }

    private void saveQuestionTagRel(Long questionId, Long tagId, String categoryCode, String createdBy) {
        if (questionTagRelRepository.existsByQuestionIdAndTagId(questionId, tagId)) {
            return;
        }
        QuestionTagRel rel = new QuestionTagRel();
        rel.setQuestionId(questionId);
        rel.setTagId(tagId);
        rel.setCategoryCode(categoryCode);
        rel.setCreatedBy(createdBy);
        questionTagRelRepository.save(rel);
    }
}
