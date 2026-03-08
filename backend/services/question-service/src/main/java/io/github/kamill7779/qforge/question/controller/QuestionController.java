package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.question.dto.CreateQuestionRequest;
import io.github.kamill7779.qforge.question.dto.AddAnswerResponse;
import io.github.kamill7779.qforge.question.dto.CreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.AiTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.AiTaskResponse;
import io.github.kamill7779.qforge.question.dto.ApplyAiRecommendationRequest;
import io.github.kamill7779.qforge.question.dto.ExportWordRequest;
import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.UpdateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.OcrTaskSubmitRequest;
import io.github.kamill7779.qforge.question.dto.QuestionOverviewResponse;
import io.github.kamill7779.qforge.question.dto.QuestionAssetResponse;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import io.github.kamill7779.qforge.question.dto.UpdateDifficultyRequest;
import io.github.kamill7779.qforge.question.dto.UpdateStemRequest;
import io.github.kamill7779.qforge.question.dto.UpdateTagsRequest;
import io.github.kamill7779.qforge.question.service.ExportService;
import io.github.kamill7779.qforge.question.service.QuestionCommandService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionCommandService questionCommandService;
    private final ExportService exportService;

    public QuestionController(QuestionCommandService questionCommandService, ExportService exportService) {
        this.questionCommandService = questionCommandService;
        this.exportService = exportService;
    }

    @GetMapping
    public ResponseEntity<List<QuestionOverviewResponse>> listQuestions(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.listUserQuestions(requestUser));
    }

    @GetMapping("/{questionUuid}/assets")
    public ResponseEntity<List<QuestionAssetResponse>> listAssets(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.listAssets(questionUuid, requestUser));
    }

    @PostMapping("/export/word")
    public ResponseEntity<byte[]> exportWord(
            @RequestBody ExportWordRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        byte[] docx = exportService.exportQuestionsWord(request, requestUser);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + request.safeTitle() + ".docx\"")
                .body(docx);
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

    @PutMapping("/{questionUuid}/stem")
    public ResponseEntity<QuestionStatusResponse> updateStem(
            @PathVariable("questionUuid") String questionUuid,
            @Valid @RequestBody UpdateStemRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.updateStem(questionUuid, request, requestUser));
    }

    @PostMapping("/{questionUuid}/complete")
    public ResponseEntity<QuestionStatusResponse> complete(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.completeQuestion(questionUuid, requestUser));
    }

    @PostMapping("/{questionUuid}/answers")
    public ResponseEntity<AddAnswerResponse> addAnswer(
            @PathVariable("questionUuid") String questionUuid,
            @Valid @RequestBody CreateAnswerRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.status(201).body(questionCommandService.addAnswer(questionUuid, request, requestUser));
    }

    @PutMapping("/{questionUuid}/answers/{answerUuid}")
    public ResponseEntity<QuestionStatusResponse> updateAnswer(
            @PathVariable("questionUuid") String questionUuid,
            @PathVariable("answerUuid") String answerUuid,
            @Valid @RequestBody UpdateAnswerRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.updateAnswer(questionUuid, answerUuid, request, requestUser));
    }

    @DeleteMapping("/{questionUuid}/answers/{answerUuid}")
    public ResponseEntity<Void> deleteAnswer(
            @PathVariable("questionUuid") String questionUuid,
            @PathVariable("answerUuid") String answerUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        questionCommandService.deleteAnswer(questionUuid, answerUuid, requestUser);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{questionUuid}/ocr-tasks")
    public ResponseEntity<OcrTaskAcceptedResponse> submitOcrTask(
            @PathVariable("questionUuid") String questionUuid,
            @Valid @RequestBody OcrTaskSubmitRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.accepted().body(
                questionCommandService.submitOcrTask(questionUuid, request, requestUser)
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

    @PutMapping("/{questionUuid}/tags")
    public ResponseEntity<QuestionStatusResponse> updateTags(
            @PathVariable("questionUuid") String questionUuid,
            @Valid @RequestBody UpdateTagsRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.updateTags(questionUuid, request, requestUser));
    }

    @PutMapping("/{questionUuid}/difficulty")
    public ResponseEntity<QuestionStatusResponse> updateDifficulty(
            @PathVariable("questionUuid") String questionUuid,
            @Valid @RequestBody UpdateDifficultyRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.updateDifficulty(questionUuid, request, requestUser));
    }

    @PostMapping("/{questionUuid}/ai-analysis")
    public ResponseEntity<AiTaskAcceptedResponse> requestAiAnalysis(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        AiTaskAcceptedResponse response = questionCommandService.requestAiAnalysis(questionUuid, requestUser);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{questionUuid}/ai-tasks")
    public ResponseEntity<List<AiTaskResponse>> listAiTasks(
            @PathVariable("questionUuid") String questionUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.listAiTasks(questionUuid, requestUser));
    }

    @PutMapping("/{questionUuid}/ai-tasks/{taskUuid}/apply")
    public ResponseEntity<QuestionStatusResponse> applyAiRecommendation(
            @PathVariable("questionUuid") String questionUuid,
            @PathVariable("taskUuid") String taskUuid,
            @Valid @RequestBody ApplyAiRecommendationRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(questionCommandService.applyAiRecommendation(questionUuid, taskUuid, request, requestUser));
    }
}
