package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.question.dto.exam.QuestionTypeResponse;
import io.github.kamill7779.qforge.question.dto.exam.SaveQuestionTypeRequest;
import io.github.kamill7779.qforge.question.service.QuestionTypeService;
import java.util.List;
import org.springframework.http.HttpStatus;
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

/**
 * 题型配置 API — 系统预置 + 用户自定义。
 */
@RestController
@RequestMapping("/api/question-types")
public class QuestionTypeController {

    private final QuestionTypeService questionTypeService;

    public QuestionTypeController(QuestionTypeService questionTypeService) {
        this.questionTypeService = questionTypeService;
    }

    /**
     * 列出当前用户可用的所有题型。
     */
    @GetMapping
    public ResponseEntity<List<QuestionTypeResponse>> listTypes(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionTypeService.listForUser(requestUser));
    }

    /**
     * 创建自定义题型。
     */
    @PostMapping
    public ResponseEntity<QuestionTypeResponse> createType(
            @RequestBody SaveQuestionTypeRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        QuestionTypeResponse response = questionTypeService.createCustom(request, requestUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 更新自定义题型。
     */
    @PutMapping("/{id}")
    public ResponseEntity<QuestionTypeResponse> updateType(
            @PathVariable Long id,
            @RequestBody SaveQuestionTypeRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionTypeService.updateCustom(id, request, requestUser));
    }

    /**
     * 删除自定义题型。
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteType(
            @PathVariable Long id,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        questionTypeService.deleteCustom(id, requestUser);
        return ResponseEntity.noContent().build();
    }
}
