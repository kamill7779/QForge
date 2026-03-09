package io.github.kamill7779.qforge.gaokaocorpus.controller;

import io.github.kamill7779.qforge.gaokaocorpus.dto.MaterializeRequest;
import io.github.kamill7779.qforge.gaokaocorpus.service.MaterializationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gaokao/materialize")
public class MaterializationController {

    private final MaterializationService materializationService;

    public MaterializationController(MaterializationService materializationService) {
        this.materializationService = materializationService;
    }

    @PostMapping
    public ResponseEntity<Void> materialize(
            @Valid @RequestBody MaterializeRequest request,
            @RequestHeader(value = "X-Auth-User", defaultValue = "anonymous") String requestUser) {
        materializationService.materialize(request.getGkQuestionId(), requestUser);
        return ResponseEntity.ok().build();
    }
}
