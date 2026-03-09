package io.github.kamill7779.qforge.gaokaocorpus.controller;

import io.github.kamill7779.qforge.gaokaocorpus.dto.DraftPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.IngestSessionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.service.DraftService;
import io.github.kamill7779.qforge.gaokaocorpus.service.IngestService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gaokao/ingest-sessions")
public class IngestSessionController {

    private final IngestService ingestService;
    private final DraftService draftService;

    public IngestSessionController(IngestService ingestService, DraftService draftService) {
        this.ingestService = ingestService;
        this.draftService = draftService;
    }

    @PostMapping
    public ResponseEntity<IngestSessionDTO> createSession(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ingestService.createSession(requestUser));
    }

    @GetMapping
    public ResponseEntity<List<IngestSessionDTO>> listSessions(
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        return ResponseEntity.ok(ingestService.listSessions(requestUser));
    }

    @GetMapping("/{sessionUuid}")
    public ResponseEntity<IngestSessionDTO> getSession(
            @PathVariable("sessionUuid") String sessionUuid) {
        return ResponseEntity.ok(ingestService.getSession(sessionUuid));
    }

    @PostMapping("/{sessionUuid}/ocr-split")
    public ResponseEntity<Void> triggerOcrSplit(
            @PathVariable("sessionUuid") String sessionUuid) {
        ingestService.triggerOcrSplit(sessionUuid);
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/{sessionUuid}/draft-paper")
    public ResponseEntity<DraftPaperDTO> getDraftPaper(
            @PathVariable("sessionUuid") String sessionUuid) {
        return ResponseEntity.ok(draftService.getDraftPaper(sessionUuid));
    }
}
