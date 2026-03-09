package io.github.kamill7779.qforge.gaokaocorpus.controller;

import io.github.kamill7779.qforge.gaokaocorpus.entity.GkTaxonomyNode;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkTaxonomyNodeMapper;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/gaokao/taxonomy")
public class TaxonomyController {

    private final GkTaxonomyNodeMapper taxonomyNodeMapper;

    public TaxonomyController(GkTaxonomyNodeMapper taxonomyNodeMapper) {
        this.taxonomyNodeMapper = taxonomyNodeMapper;
    }

    @GetMapping
    public ResponseEntity<List<GkTaxonomyNode>> listTaxonomyNodes() {
        return ResponseEntity.ok(taxonomyNodeMapper.selectList(null));
    }
}
