package io.github.kamill7779.qforge.exam.controller;

import io.github.kamill7779.qforge.exam.service.ExamPaperService;
import io.github.kamill7779.qforge.internal.api.BasketComposeCreateExamRequest;
import io.github.kamill7779.qforge.internal.api.InternalExamPaperDetailDTO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/exam-papers")
public class InternalExamPaperController {

    private final ExamPaperService examPaperService;

    public InternalExamPaperController(ExamPaperService examPaperService) {
        this.examPaperService = examPaperService;
    }

    @PostMapping("/from-basket-compose")
    public ResponseEntity<InternalExamPaperDetailDTO> createFromBasketCompose(
            @RequestBody BasketComposeCreateExamRequest request,
            @RequestParam("ownerUser") String ownerUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(examPaperService.createFromBasketCompose(request, ownerUser));
    }
}
