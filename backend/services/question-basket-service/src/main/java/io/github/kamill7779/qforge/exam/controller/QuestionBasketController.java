package io.github.kamill7779.qforge.exam.controller;

import io.github.kamill7779.qforge.exam.dto.BasketItemResponse;
import io.github.kamill7779.qforge.exam.dto.compose.BasketComposeDetailResponse;
import io.github.kamill7779.qforge.exam.dto.compose.SaveBasketComposeContentRequest;
import io.github.kamill7779.qforge.exam.dto.compose.UpdateBasketComposeMetaRequest;
import io.github.kamill7779.qforge.exam.dto.exam.ExamPaperDetailResponse;
import io.github.kamill7779.qforge.exam.service.QuestionBasketService;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/question-basket")
public class QuestionBasketController {

    private final QuestionBasketService basketService;

    public QuestionBasketController(QuestionBasketService basketService) {
        this.basketService = basketService;
    }

    @GetMapping
    public ResponseEntity<List<BasketItemResponse>> listItems(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(basketService.listItems(requestUser));
    }

    @GetMapping("/uuids")
    public ResponseEntity<List<String>> listUuids(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(basketService.listUuids(requestUser));
    }

    @PostMapping("/{questionUuid}")
    public ResponseEntity<Void> addItem(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        basketService.addItem(questionUuid, requestUser);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{questionUuid}/toggle")
    public ResponseEntity<Map<String, Boolean>> toggleItem(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        boolean inBasket = basketService.toggleItem(questionUuid, requestUser);
        return ResponseEntity.ok(Map.of("inBasket", inBasket));
    }

    @DeleteMapping("/{questionUuid}")
    public ResponseEntity<Void> removeItem(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        basketService.removeItem(questionUuid, requestUser);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    public ResponseEntity<Void> clearBasket(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        basketService.clearBasket(requestUser);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/compose")
    public ResponseEntity<BasketComposeDetailResponse> getCompose(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(basketService.getCompose(requestUser));
    }

    @PutMapping("/compose/meta")
    public ResponseEntity<BasketComposeDetailResponse> updateComposeMeta(
            @RequestBody UpdateBasketComposeMetaRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(basketService.updateComposeMeta(request, requestUser));
    }

    @PutMapping("/compose/content")
    public ResponseEntity<BasketComposeDetailResponse> saveComposeContent(
            @RequestBody SaveBasketComposeContentRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(basketService.saveComposeContent(request, requestUser));
    }

    @PostMapping("/compose/confirm")
    public ResponseEntity<ExamPaperDetailResponse> confirmCompose(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(basketService.confirmCompose(requestUser));
    }
}
