package io.github.kamill7779.qforge.exam.controller;

import io.github.kamill7779.qforge.exam.dto.exam.CreateExamPaperRequest;
import io.github.kamill7779.qforge.exam.dto.exam.ExamPaperDetailResponse;
import io.github.kamill7779.qforge.exam.dto.exam.ExamPaperExportRequest;
import io.github.kamill7779.qforge.exam.dto.exam.ExamPaperOverviewResponse;
import io.github.kamill7779.qforge.exam.dto.exam.SaveExamContentRequest;
import io.github.kamill7779.qforge.exam.dto.exam.UpdateExamPaperRequest;
import io.github.kamill7779.qforge.exam.service.ExamPaperService;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

@RestController
@RequestMapping("/api/exam-papers")
public class ExamPaperController {

    private final ExamPaperService examPaperService;

    public ExamPaperController(ExamPaperService examPaperService) {
        this.examPaperService = examPaperService;
    }

    @GetMapping
    public ResponseEntity<List<ExamPaperOverviewResponse>> listPapers(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(examPaperService.listPapers(requestUser));
    }

    @PostMapping
    public ResponseEntity<ExamPaperDetailResponse> createPaper(
            @RequestBody CreateExamPaperRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(examPaperService.createPaper(request, requestUser));
    }

    @GetMapping("/{paperUuid}")
    public ResponseEntity<ExamPaperDetailResponse> getPaperDetail(
            @PathVariable("paperUuid") String paperUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(examPaperService.getPaperDetail(paperUuid, requestUser));
    }

    @PutMapping("/{paperUuid}")
    public ResponseEntity<ExamPaperDetailResponse> updatePaper(
            @PathVariable("paperUuid") String paperUuid,
            @RequestBody UpdateExamPaperRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(examPaperService.updatePaper(paperUuid, request, requestUser));
    }

    @DeleteMapping("/{paperUuid}")
    public ResponseEntity<Void> deletePaper(
            @PathVariable("paperUuid") String paperUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        examPaperService.deletePaper(paperUuid, requestUser);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{paperUuid}/content")
    public ResponseEntity<ExamPaperDetailResponse> saveContent(
            @PathVariable("paperUuid") String paperUuid,
            @RequestBody SaveExamContentRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(examPaperService.saveContent(paperUuid, request, requestUser));
    }

    @PostMapping("/{paperUuid}/export/word")
    public ResponseEntity<byte[]> exportWord(
            @PathVariable("paperUuid") String paperUuid,
            @RequestBody ExamPaperExportRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        byte[] docBytes = examPaperService.exportPaperWord(paperUuid, request, requestUser);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("exam-paper.docx").build());
        return new ResponseEntity<>(docBytes, headers, HttpStatus.OK);
    }
}
