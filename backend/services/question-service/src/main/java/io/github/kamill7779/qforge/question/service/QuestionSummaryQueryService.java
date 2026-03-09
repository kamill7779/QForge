package io.github.kamill7779.qforge.question.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.internal.api.QuestionSummaryDTO;
import io.github.kamill7779.qforge.question.config.QForgeCacheProperties;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.repository.AnswerRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class QuestionSummaryQueryService {

    private static final String SUMMARY_CACHE_PREFIX = "qforge:question-summary:";

    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final QForgeCacheProperties cacheProperties;

    public QuestionSummaryQueryService(
            QuestionRepository questionRepository,
            AnswerRepository answerRepository,
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            QForgeCacheProperties cacheProperties
    ) {
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.cacheProperties = cacheProperties;
    }

    public List<QuestionSummaryDTO> getSummaries(List<String> questionUuids, String ownerUser) {
        if (questionUuids == null || questionUuids.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> dedupedUuids = questionUuids.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<String> orderedUuids = new ArrayList<>(dedupedUuids);
        if (orderedUuids.isEmpty()) {
            return List.of();
        }

        Map<String, QuestionSummaryDTO> resolved = new LinkedHashMap<>();
        List<String> misses = new ArrayList<>();
        for (String questionUuid : orderedUuids) {
            QuestionSummaryDTO cached = readCached(ownerUser, questionUuid);
            if (cached != null) {
                resolved.put(questionUuid, cached);
            } else {
                misses.add(questionUuid);
            }
        }

        if (!misses.isEmpty()) {
            List<Question> questions = questionRepository.findByQuestionUuidsAndOwnerUser(misses, ownerUser);
            List<Long> questionIds = questions.stream().map(Question::getId).toList();
            Map<Long, Long> answerCounts = answerRepository.countByQuestionIds(questionIds);
            for (Question question : questions) {
                QuestionSummaryDTO summary = toSummary(question, answerCounts.getOrDefault(question.getId(), 0L));
                resolved.put(question.getQuestionUuid(), summary);
                writeCached(ownerUser, summary);
            }
        }

        return orderedUuids.stream()
                .map(resolved::get)
                .filter(Objects::nonNull)
                .toList();
    }

    public void evict(String ownerUser, String questionUuid) {
        if (ownerUser == null || ownerUser.isBlank() || questionUuid == null || questionUuid.isBlank()) {
            return;
        }
        redis.delete(buildCacheKey(ownerUser, questionUuid));
    }

    public void evict(Question question) {
        if (question == null) {
            return;
        }
        evict(question.getOwnerUser(), question.getQuestionUuid());
    }

    private QuestionSummaryDTO toSummary(Question question, long answerCount) {
        return new QuestionSummaryDTO(
                question.getId(),
                question.getQuestionUuid(),
                question.getStatus(),
                question.getStemText(),
                question.getDifficulty(),
                question.getSource() != null ? question.getSource() : "未分类",
                answerCount
        );
    }

    private QuestionSummaryDTO readCached(String ownerUser, String questionUuid) {
        try {
            String cached = redis.opsForValue().get(buildCacheKey(ownerUser, questionUuid));
            if (cached == null || cached.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cached, QuestionSummaryDTO.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void writeCached(String ownerUser, QuestionSummaryDTO summary) {
        try {
            Duration ttl = Duration.ofSeconds(cacheProperties.getQuestionSummaryTtlSeconds());
            redis.opsForValue().set(
                    buildCacheKey(ownerUser, summary.questionUuid()),
                    objectMapper.writeValueAsString(summary),
                    ttl
            );
        } catch (Exception ignored) {
            // Cache failure must not break the main query path.
        }
    }

    private String buildCacheKey(String ownerUser, String questionUuid) {
        return SUMMARY_CACHE_PREFIX + ownerUser + ":" + questionUuid;
    }
}
