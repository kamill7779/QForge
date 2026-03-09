package io.github.kamill7779.qforge.gaokaocorpus.service;

import io.github.kamill7779.qforge.gaokaocorpus.dto.IngestSessionDTO;
import java.util.List;

public interface IngestService {

    IngestSessionDTO createSession(String operatorUser);

    IngestSessionDTO getSession(String sessionUuid);

    List<IngestSessionDTO> listSessions(String operatorUser);

    void triggerOcrSplit(String sessionUuid);
}
