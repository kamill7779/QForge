package io.github.kamill7779.qforge.gaokaocorpus.service;

import io.github.kamill7779.qforge.gaokaocorpus.client.GaokaoAnalysisClient;
import io.github.kamill7779.qforge.gaokaocorpus.dto.IngestSessionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSessionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSourceFileMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IngestServiceImpl implements IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestServiceImpl.class);

    private final GkIngestSessionMapper ingestSessionMapper;
    private final GkIngestSourceFileMapper ingestSourceFileMapper;
    private final GaokaoAnalysisClient analysisClient;

    public IngestServiceImpl(
            GkIngestSessionMapper ingestSessionMapper,
            GkIngestSourceFileMapper ingestSourceFileMapper,
            GaokaoAnalysisClient analysisClient
    ) {
        this.ingestSessionMapper = ingestSessionMapper;
        this.ingestSourceFileMapper = ingestSourceFileMapper;
        this.analysisClient = analysisClient;
    }

    @Override
    public IngestSessionDTO createSession(String operatorUser) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public IngestSessionDTO getSession(String sessionUuid) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public List<IngestSessionDTO> listSessions(String operatorUser) {
        // TODO: implement
        throw new UnsupportedOperationException("Not yet implemented");
    }

    @Override
    public void triggerOcrSplit(String sessionUuid) {
        // TODO: implement — update session status, delegate OCR to analysis-service via Feign/MQ
        throw new UnsupportedOperationException("Not yet implemented");
    }
}
