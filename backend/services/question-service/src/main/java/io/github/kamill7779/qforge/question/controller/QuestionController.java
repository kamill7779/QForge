package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.question.dto.CreateQuestionRequest;
import io.github.kamill7779.qforge.question.dto.CreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskSubmitRequest;
import io.github.kamill7779.qforge.question.dto.QuestionOverviewResponse;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import io.github.kamill7779.qforge.question.service.QuestionCommandService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionCommandService questionCommandService;

    public QuestionController(QuestionCommandService questionCommandService) {
        this.questionCommandService = questionCommandService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionOverviewResponse>> listQuestions(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.listUserQuestions(requestUser));
    }

    @PostMapping
    public ResponseEntity<QuestionStatusResponse> createDraft(
            @Valid @RequestBody(required = false) CreateQuestionRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        CreateQuestionRequest safeRequest = request == null ? new CreateQuestionRequest() : request;
        QuestionStatusResponse response = questionCommandService.createDraft(safeRequest, requestUser);
        return ResponseEntity
                .created(ServletUriComponentsBuilder.fromCurrentRequest()
                        .path("/{id}")
                        .buildAndExpand(response.questionUuid())
                        .toUri())
                .body(response);
    }

    @PostMapping("/{questionUuid}/complete")
    public ResponseEntity<QuestionStatusResponse> complete(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.completeQuestion(questionUuid, requestUser));
    }

    @PostMapping("/{questionUuid}/answers")
    public ResponseEntity<QuestionStatusResponse> addAnswer(
            @PathVariable("questionUuid") String questionUuid,
            @Valid @RequestBody CreateAnswerRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.status(201).body(questionCommandService.addAnswer(questionUuid, request, requestUser));
    }

    @PostMapping("/{questionUuid}/ocr-tasks")
    public ResponseEntity<OcrTaskAcceptedResponse> submitQuestionOcr(
            @PathVariable("questionUuid") String questionUuid,
            @Valid @RequestBody OcrTaskSubmitRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.accepted().body(
                questionCommandService.submitQuestionStemOcr(questionUuid, request, requestUser)
        );
    }

    @DeleteMapping("/{questionUuid}")
    public ResponseEntity<Void> deleteDraftQuestion(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        questionCommandService.deleteDraftQuestion(questionUuid, requestUser);
        return ResponseEntity.noContent().build();
    }
}
