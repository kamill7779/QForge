package io.github.kamill7779.qforge.gaokaocorpus.service;

import io.github.kamill7779.qforge.gaokaocorpus.dto.GkPaperDTO;

public interface PublishService {

    GkPaperDTO publishPaper(String draftPaperUuid);
}
