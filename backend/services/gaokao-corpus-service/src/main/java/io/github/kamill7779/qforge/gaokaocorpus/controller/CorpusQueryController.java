package io.github.kamill7779.qforge.gaokaocorpus.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkQuestionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.service.CorpusQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gaokao/corpus")
public class CorpusQueryController {

    private final CorpusQueryService corpusQueryService;

    public CorpusQueryController(CorpusQueryService corpusQueryService) {
        this.corpusQueryService = corpusQueryService;
    }

    @GetMapping("/papers")
    public ResponseEntity<Page<GkPaperDTO>> listPapers(
            @RequestParam(value = "examYear", required = false) Short examYear,
            @RequestParam(value = "provinceCode", required = false) String provinceCode,
            @RequestParam(value = "paperTypeCode", required = false) String paperTypeCode,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ResponseEntity.ok(corpusQueryService.listPapers(examYear, provinceCode, paperTypeCode, page, size));
    }

    @GetMapping("/papers/{uuid}")
    public ResponseEntity<GkPaperDTO> getPaper(
            @PathVariable("uuid") String uuid) {
        return ResponseEntity.ok(corpusQueryService.getPaper(uuid));
    }

    @GetMapping("/questions/{uuid}")
    public ResponseEntity<GkQuestionDTO> getQuestion(
            @PathVariable("uuid") String uuid) {
        return ResponseEntity.ok(corpusQueryService.getQuestion(uuid));
    }
}
