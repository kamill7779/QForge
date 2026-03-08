package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.question.dto.BasketItemResponse;
import io.github.kamill7779.qforge.question.service.QuestionBasketService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 试题篮（购物车）API。
 *
 * <ul>
 *   <li>GET  /api/question-basket        — 列出篮中所有题（含基本信息）</li>
 *   <li>GET  /api/question-basket/uuids  — 仅返回 UUID 列表（轻量级）</li>
 *   <li>POST /api/question-basket/{uuid} — 添加题目</li>
 *   <li>POST /api/question-basket/{uuid}/toggle — 切换选中状态</li>
 *   <li>DELETE /api/question-basket/{uuid} — 移除题目</li>
 *   <li>DELETE /api/question-basket       — 清空篮</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/question-basket")
public class QuestionBasketController {

    private final QuestionBasketService basketService;

    public QuestionBasketController(QuestionBasketService basketService) {
        this.basketService = basketService;
    }

    /**
     * 列出篮中所有题目（含 stemText 等信息）。
     */
    @GetMapping
    public ResponseEntity<List<BasketItemResponse>> listItems(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(basketService.listItems(requestUser));
    }

    /**
     * 仅返回篮中题目 UUID 列表（轻量级，用于前端选中状态同步）。
     */
    @GetMapping("/uuids")
    public ResponseEntity<List<String>> listUuids(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(basketService.listUuids(requestUser));
    }

    /**
     * 添加题目到篮（幂等）。
     */
    @PostMapping("/{questionUuid}")
    public ResponseEntity<Void> addItem(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        basketService.addItem(questionUuid, requestUser);
        return ResponseEntity.ok().build();
    }

    /**
     * 切换题目选中状态（有则移除，无则添加）。
     */
    @PostMapping("/{questionUuid}/toggle")
    public ResponseEntity<Map<String, Boolean>> toggleItem(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        boolean inBasket = basketService.toggleItem(questionUuid, requestUser);
        return ResponseEntity.ok(Map.of("inBasket", inBasket));
    }

    /**
     * 从篮中移除题目。
     */
    @DeleteMapping("/{questionUuid}")
    public ResponseEntity<Void> removeItem(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        basketService.removeItem(questionUuid, requestUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * 清空篮。
     */
    @DeleteMapping
    public ResponseEntity<Void> clearBasket(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        basketService.clearBasket(requestUser);
        return ResponseEntity.noContent().build();
    }
}
