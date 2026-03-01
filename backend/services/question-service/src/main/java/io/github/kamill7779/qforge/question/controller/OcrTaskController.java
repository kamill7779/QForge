package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.question.dto.OcrConfirmRequest;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import io.github.kamill7779.qforge.question.service.QuestionCommandService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ocr-tasks")
public class OcrTaskController {

    private final QuestionCommandService questionCommandService;

    public OcrTaskController(QuestionCommandService questionCommandService) {
        this.questionCommandService = questionCommandService;
    }

    @PostMapping("/{taskUuid}/confirmations")
    public ResponseEntity<QuestionStatusResponse> confirmOcr(
            @PathVariable("taskUuid") String taskUuid,
            @Valid @RequestBody OcrConfirmRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.confirmOcrTask(taskUuid, request, requestUser));
    }
}

