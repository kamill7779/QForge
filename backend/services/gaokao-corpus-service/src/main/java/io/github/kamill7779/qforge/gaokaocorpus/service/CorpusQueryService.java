package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkPaperDTO;
import io.github.kamill7779.qforge.gaokaocorpus.dto.GkQuestionDTO;
import java.util.List;

public interface CorpusQueryService {

    GkPaperDTO getPaper(String paperUuid);

    Page<GkPaperDTO> listPapers(Short examYear, String provinceCode, String paperTypeCode, int page, int size);

    GkQuestionDTO getQuestion(String questionUuid);

    List<GkQuestionDTO> searchSimilar(String questionUuid);
}
