package io.github.kamill7779.qforge.gaokaocorpus.controller;

import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftQuestionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.UpdateDraftPaperRequest;
import io.github.kamill7779.qforge.gaokaocorpus.dto.UpdateDraftQuestionRequest;
import io.github.kamill7779.qforge.gaokaocorpus.service.DraftService;
import io.github.kamill7779.qforge.gaokaocorpus.service.PublishService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gaokao")
public class DraftController {

    private final DraftService draftService;
    private final PublishService publishService;

    public DraftController(DraftService draftService, PublishService publishService) {
        this.draftService = draftService;
        this.publishService = publishService;
    }

    @PutMapping("/draft-papers/{uuid}")
    public ResponseEntity<DraftPaperDTO> updateDraftPaper(
            @PathVariable("uuid") String uuid,
            @Valid @RequestBody UpdateDraftPaperRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(draftService.updateDraftPaper(uuid, request));
    }

    @PutMapping("/draft-questions/{uuid}")
    public ResponseEntity<DraftQuestionDTO> updateDraftQuestion(
            @PathVariable("uuid") String uuid,
            @Valid @RequestBody UpdateDraftQuestionRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(draftService.updateDraftQuestion(uuid, request));
    }

    @PostMapping("/draft-questions/{uuid}/analyze")
    public ResponseEntity<Void> triggerAnalyze(
            @PathVariable("uuid") String uuid) {
        draftService.triggerAnalyze(uuid);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/draft-papers/{uuid}/analyze")
    public ResponseEntity<Void> triggerBatchAnalyze(
            @PathVariable("uuid") String uuid) {
        draftService.triggerBatchAnalyze(uuid);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/draft-questions/{uuid}/confirm")
    public ResponseEntity<Void> confirmProfile(
            @PathVariable("uuid") String uuid) {
        draftService.confirmProfile(uuid);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/draft-papers/{uuid}/publish")
    public ResponseEntity<GkPaperDTO> publishPaper(
            @PathVariable("uuid") String uuid) {
        return ResponseEntity.ok(publishService.publishPaper(uuid));
    }
}
