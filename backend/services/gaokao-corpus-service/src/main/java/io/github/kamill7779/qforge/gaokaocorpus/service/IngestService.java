package io.github.kamill7779.qforge.gaokaocorpus.service;

import io.github.kamill7779.qforge.gaokaocorpus.dto.IngestSessionDTO;
import java.util.List;
import org.springframework.web.multipart.MultipartFile;

public interface IngestService {

    IngestSessionDTO createSession(String operatorUser);

    IngestSessionDTO getSession(String sessionUuid);

    List<IngestSessionDTO> listSessions(String operatorUser);

    void uploadFiles(String sessionUuid, MultipartFile[] files);

    void triggerOcrSplit(String sessionUuid);
}
