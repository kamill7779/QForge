package io.github.kamill7779.qforge.exam.service;

import io.github.kamill7779.qforge.internal.api.QuestionCoreClient;
import io.github.kamill7779.qforge.internal.api.QuestionSummaryDTO;
import io.github.kamill7779.qforge.exam.dto.BasketItemResponse;
import io.github.kamill7779.qforge.exam.entity.QuestionBasket;
import io.github.kamill7779.qforge.exam.exception.BusinessValidationException;
import io.github.kamill7779.qforge.exam.repository.QuestionBasketRepository;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 试题篮（购物车）服务 — 通过 QuestionCoreClient Feign 调用 question-core-service 校验题目。
 */
@Service
public class QuestionBasketService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBasketService.class);

    private final QuestionBasketRepository basketRepository;
    private final QuestionCoreClient questionCoreClient;
    private final ExamCacheService examCacheService;

    public QuestionBasketService(
            QuestionBasketRepository basketRepository,
            QuestionCoreClient questionCoreClient,
            ExamCacheService examCacheService
    ) {
        this.basketRepository = basketRepository;
        this.questionCoreClient = questionCoreClient;
        this.examCacheService = examCacheService;
    }

    public List<BasketItemResponse> listItems(String requestUser) {
        return examCacheService.getBasketItems(requestUser, () -> {
            List<QuestionBasket> items = basketRepository.findByOwnerUser(requestUser);
            if (items.isEmpty()) {
                return List.of();
            }

            List<String> uuids = items.stream().map(QuestionBasket::getQuestionUuid).toList();

            Map<String, QuestionSummaryDTO> summaryMap;
            try {
                List<QuestionSummaryDTO> summaries = questionCoreClient.batchGetSummaries(String.join(",", uuids), requestUser);
                summaryMap = summaries.stream()
                        .collect(Collectors.toMap(QuestionSummaryDTO::questionUuid, s -> s));
            } catch (Exception e) {
                log.warn("Failed to fetch question summaries from question-core-service: {}", e.getMessage());
                summaryMap = Map.of();
            }

            Map<String, QuestionSummaryDTO> finalSummaryMap = summaryMap;
            return items.stream().map(item -> {
                QuestionSummaryDTO summary = finalSummaryMap.get(item.getQuestionUuid());
                return new BasketItemResponse(
                        item.getQuestionUuid(),
                        summary != null ? summary.stemText() : null,
                        summary != null ? summary.source() : null,
                        summary != null ? summary.difficulty() : null,
                        item.getAddedAt()
                );
            }).toList();
        });
    }

    public List<String> listUuids(String requestUser) {
        return examCacheService.getBasketUuids(
                requestUser,
                () -> basketRepository.findByOwnerUser(requestUser)
                        .stream()
                        .map(QuestionBasket::getQuestionUuid)
                        .toList()
        );
    }

    @Transactional
    public void addItem(String questionUuid, String requestUser) {
        // Feign 调用 question-core-service 校验题目存在性
        Map<String, Object> existsResult = questionCoreClient.checkExists(questionUuid, requestUser);
        Boolean exists = (Boolean) existsResult.get("exists");
        if (!Boolean.TRUE.equals(exists)) {
            throw new BusinessValidationException(
                    "QUESTION_NOT_FOUND", "题目不存在",
                    Map.of("questionUuid", questionUuid), HttpStatus.NOT_FOUND);
        }

        if (basketRepository.findByOwnerAndQuestionUuid(requestUser, questionUuid).isPresent()) {
            log.debug("Question {} already in basket for user {}", questionUuid, requestUser);
            return;
        }

        QuestionBasket item = new QuestionBasket();
        item.setOwnerUser(requestUser);
        item.setQuestionUuid(questionUuid);
        // questionId 不再需要，跨服务通过 UUID 关联
        basketRepository.insert(item);
        examCacheService.evictBasket(requestUser);
        log.info("User [{}] added question {} to basket", requestUser, questionUuid);
    }

    @Transactional
    public void removeItem(String questionUuid, String requestUser) {
        basketRepository.findByOwnerAndQuestionUuid(requestUser, questionUuid)
                .ifPresent(item -> {
                    basketRepository.deleteById(item.getId());
                    examCacheService.evictBasket(requestUser);
                    log.info("User [{}] removed question {} from basket", requestUser, questionUuid);
                });
    }

    @Transactional
    public boolean toggleItem(String questionUuid, String requestUser) {
        var existing = basketRepository.findByOwnerAndQuestionUuid(requestUser, questionUuid);
        if (existing.isPresent()) {
            basketRepository.deleteById(existing.get().getId());
            examCacheService.evictBasket(requestUser);
            log.info("User [{}] toggled question {} OUT of basket", requestUser, questionUuid);
            return false;
        } else {
            addItem(questionUuid, requestUser);
            return true;
        }
    }

    @Transactional
    public void clearBasket(String requestUser) {
        basketRepository.deleteByOwnerUser(requestUser);
        examCacheService.evictBasket(requestUser);
        log.info("User [{}] cleared basket", requestUser);
    }
}
