package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.question.dto.BasketItemResponse;
import io.github.kamill7779.qforge.question.entity.Question;
import io.github.kamill7779.qforge.question.entity.QuestionBasket;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.repository.QuestionBasketRepository;
import io.github.kamill7779.qforge.question.repository.QuestionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 试题篮（购物车）服务 — 管理用户的选题篮。
 */
@Service
public class QuestionBasketService {

    private static final Logger log = LoggerFactory.getLogger(QuestionBasketService.class);

    private final QuestionBasketRepository basketRepository;
    private final QuestionRepository questionRepository;

    public QuestionBasketService(
            QuestionBasketRepository basketRepository,
            QuestionRepository questionRepository
    ) {
        this.basketRepository = basketRepository;
        this.questionRepository = questionRepository;
    }

    /**
     * 列出用户篮中所有题目（含题目基本信息）。
     */
    public List<BasketItemResponse> listItems(String requestUser) {
        List<QuestionBasket> items = basketRepository.findByOwnerUser(requestUser);
        if (items.isEmpty()) {
            return List.of();
        }

        // 批量加载题目信息
        List<String> uuids = items.stream().map(QuestionBasket::getQuestionUuid).toList();
        Map<String, Question> questionMap = questionRepository
                .findByQuestionUuidsAndOwnerUser(uuids, requestUser)
                .stream()
                .collect(Collectors.toMap(Question::getQuestionUuid, q -> q));

        return items.stream().map(item -> {
            Question q = questionMap.get(item.getQuestionUuid());
            return new BasketItemResponse(
                    item.getQuestionUuid(),
                    q != null ? q.getStemText() : null,
                    q != null ? q.getSource() : null,
                    q != null ? q.getDifficulty() : null,
                    item.getAddedAt()
            );
        }).toList();
    }

    /**
     * 返回用户篮中所有题目的 UUID 集合（轻量级，用于前端快速判断选中状态）。
     */
    public List<String> listUuids(String requestUser) {
        return basketRepository.findByOwnerUser(requestUser)
                .stream()
                .map(QuestionBasket::getQuestionUuid)
                .toList();
    }

    /**
     * 添加题目到篮。
     */
    @Transactional
    public void addItem(String questionUuid, String requestUser) {
        // 校验题目存在
        Question question = questionRepository.findByQuestionUuidAndOwnerUser(questionUuid, requestUser)
                .orElseThrow(() -> new BusinessValidationException(
                        "QUESTION_NOT_FOUND", "题目不存在",
                        Map.of("questionUuid", questionUuid), HttpStatus.NOT_FOUND));

        // 幂等：已存在则跳过
        if (basketRepository.findByOwnerAndQuestionUuid(requestUser, questionUuid).isPresent()) {
            log.debug("Question {} already in basket for user {}", questionUuid, requestUser);
            return;
        }

        QuestionBasket item = new QuestionBasket();
        item.setOwnerUser(requestUser);
        item.setQuestionId(question.getId());
        item.setQuestionUuid(questionUuid);
        basketRepository.insert(item);

        log.info("User [{}] added question {} to basket", requestUser, questionUuid);
    }

    /**
     * 从篮中移除题目。
     */
    @Transactional
    public void removeItem(String questionUuid, String requestUser) {
        basketRepository.findByOwnerAndQuestionUuid(requestUser, questionUuid)
                .ifPresent(item -> {
                    basketRepository.deleteById(item.getId());
                    log.info("User [{}] removed question {} from basket", requestUser, questionUuid);
                });
    }

    /**
     * 切换题目在篮中的状态（有则移除，无则添加）。返回操作后是否在篮中。
     */
    @Transactional
    public boolean toggleItem(String questionUuid, String requestUser) {
        var existing = basketRepository.findByOwnerAndQuestionUuid(requestUser, questionUuid);
        if (existing.isPresent()) {
            basketRepository.deleteById(existing.get().getId());
            log.info("User [{}] toggled question {} OUT of basket", requestUser, questionUuid);
            return false;
        } else {
            addItem(questionUuid, requestUser);
            return true;
        }
    }

    /**
     * 清空用户篮。
     */
    @Transactional
    public void clearBasket(String requestUser) {
        basketRepository.deleteByOwnerUser(requestUser);
        log.info("User [{}] cleared basket", requestUser);
    }

    /**
     * 统计用户篮中题目数。
     */
    public long countItems(String requestUser) {
        return basketRepository.countByOwnerUser(requestUser);
    }
}
