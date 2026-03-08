package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.question.dto.exam.CreateExamPaperRequest;
import io.github.kamill7779.qforge.question.dto.exam.ExamPaperDetailResponse;
import io.github.kamill7779.qforge.question.dto.exam.ExamPaperExportRequest;
import io.github.kamill7779.qforge.question.dto.exam.ExamPaperOverviewResponse;
import io.github.kamill7779.qforge.question.dto.exam.SaveExamContentRequest;
import io.github.kamill7779.qforge.question.dto.exam.UpdateExamPaperRequest;
import io.github.kamill7779.qforge.question.service.ExamPaperService;
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

/**
 * 试卷组卷 API。
 */
@RestController
@RequestMapping("/api/exam-papers")
public class ExamPaperController {

    private final ExamPaperService examPaperService;

    public ExamPaperController(ExamPaperService examPaperService) {
        this.examPaperService = examPaperService;
    }

    /**
     * 列表 — 当前用户的所有试卷。
     */
    @GetMapping
    public ResponseEntity<List<ExamPaperOverviewResponse>> listPapers(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(examPaperService.listPapers(requestUser));
    }

    /**
     * 创建试卷。
     */
    @PostMapping
    public ResponseEntity<ExamPaperDetailResponse> createPaper(
            @RequestBody CreateExamPaperRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        ExamPaperDetailResponse response = examPaperService.createPaper(request, requestUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * 详情 — 含完整大题 + 题目。
     */
    @GetMapping("/{paperUuid}")
    public ResponseEntity<ExamPaperDetailResponse> getPaperDetail(
            @PathVariable String paperUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(examPaperService.getPaperDetail(paperUuid, requestUser));
    }

    /**
     * 更新试卷元信息。
     */
    @PutMapping("/{paperUuid}")
    public ResponseEntity<ExamPaperDetailResponse> updatePaper(
            @PathVariable String paperUuid,
            @RequestBody UpdateExamPaperRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(examPaperService.updatePaper(paperUuid, request, requestUser));
    }

    /**
     * 删除试卷（逻辑删除）。
     */
    @DeleteMapping("/{paperUuid}")
    public ResponseEntity<Void> deletePaper(
            @PathVariable String paperUuid,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        examPaperService.deletePaper(paperUuid, requestUser);
        return ResponseEntity.noContent().build();
    }

    /**
     * 整卷内容保存 — 原子替换所有大题 + 题目。
     */
    @PutMapping("/{paperUuid}/content")
    public ResponseEntity<ExamPaperDetailResponse> saveContent(
            @PathVariable String paperUuid,
            @RequestBody SaveExamContentRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        return ResponseEntity.ok(examPaperService.saveContent(paperUuid, request, requestUser));
    }

    /**
     * 导出试卷为 Word 文档。
     */
    @PostMapping("/{paperUuid}/export/word")
    public ResponseEntity<byte[]> exportWord(
            @PathVariable String paperUuid,
            @RequestBody ExamPaperExportRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser
    ) {
        byte[] docBytes = examPaperService.exportPaperWord(paperUuid, request, requestUser);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"));
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("exam-paper.docx")
                .build());
        return new ResponseEntity<>(docBytes, headers, HttpStatus.OK);
    }
}
