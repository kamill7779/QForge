package io.github.kamill7779.qforge.exam.controller;

import io.github.kamill7779.qforge.exam.dto.exam.QuestionTypeResponse;
import io.github.kamill7779.qforge.exam.dto.exam.SaveQuestionTypeRequest;
import io.github.kamill7779.qforge.exam.service.QuestionTypeService;
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

@RestController
@RequestMapping("/api/question-types")
public class QuestionTypeController {

    private final QuestionTypeService questionTypeService;

    public QuestionTypeController(QuestionTypeService questionTypeService) {
        this.questionTypeService = questionTypeService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionTypeResponse>> listTypes(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(questionTypeService.listForUser(requestUser));
    }

    @PostMapping
    public ResponseEntity<QuestionTypeResponse> createType(
            @RequestBody SaveQuestionTypeRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(questionTypeService.createCustom(request, requestUser));
    }

    @PutMapping("/{id}")
    public ResponseEntity<QuestionTypeResponse> updateType(
            @PathVariable("id") Long id,
            @RequestBody SaveQuestionTypeRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(questionTypeService.updateCustom(id, request, requestUser));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteType(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        questionTypeService.deleteCustom(id, requestUser);
        return ResponseEntity.noContent().build();
    }
}
