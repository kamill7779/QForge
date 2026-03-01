package io.github.kamill7779.qforge.question.controller;

import io.github.kamill7779.qforge.question.dto.TagCatalogResponse;
import io.github.kamill7779.qforge.question.service.TagQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private final TagQueryService tagQueryService;

    public TagController(TagQueryService tagQueryService) {
        this.tagQueryService = tagQueryService;
    }

    @GetMapping
    public ResponseEntity<TagCatalogResponse> getTagCatalog() {
        return ResponseEntity.ok(tagQueryService.getMainTagCatalog());
    }
}

