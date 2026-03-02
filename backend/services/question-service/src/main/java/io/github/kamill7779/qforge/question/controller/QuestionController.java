package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.question.dto.BatchCreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.CreateQuestionRequest;
import io.github.kamill7779.qforge.question.dto.CreateAnswerRequest;
import io.github.kamill7779.qforge.question.dto.OcrTaskAcceptedResponse;
import io.github.kamill7779.qforge.question.dto.OcrTaskSubmitRequest;
import io.github.kamill7779.qforge.question.dto.QuestionOverviewResponse;
import io.github.kamill7779.qforge.question.dto.QuestionStatusResponse;
import io.github.kamill7779.qforge.question.dto.UpdateStemRequest;
import io.github.kamill7779.qforge.question.service.QuestionCommandService;
import jakarta.validation.Valid;
import java.util.List;
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

    /**
     * 【新增】更新题干 XML 文本。
     * 客户端收到 OCR 结果后，用户确认/修正，最终将 XML 题干文本通过此接口传递。
     * 服务端强制执行 XML 规范校验。
     */
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
    public ResponseEntity<QuestionStatusResponse> addAnswer(
            @PathVariable("questionUuid") String questionUuid,
            @Valid @RequestBody CreateAnswerRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.status(201).body(questionCommandService.addAnswer(questionUuid, request, requestUser));
    }

    /**
     * 【新增】批量添加答案。
     * 兼容 OCR 识别结果和手动输入多答案场景。
     */
    @PostMapping("/{questionUuid}/answers/batch")
    public ResponseEntity<QuestionStatusResponse> batchAddAnswers(
            @PathVariable("questionUuid") String questionUuid,
            @Valid @RequestBody BatchCreateAnswerRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.status(201).body(questionCommandService.batchAddAnswers(questionUuid, request, requestUser));
    }

    /**
     * 【重构】统一 OCR 任务提交，bizType 由请求体指定。
     * 支持 QUESTION_STEM（题干 OCR）和 ANSWER_CONTENT（答案 OCR）。
     */
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
}
