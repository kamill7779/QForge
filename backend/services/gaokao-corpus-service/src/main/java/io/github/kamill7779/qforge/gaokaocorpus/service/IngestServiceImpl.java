package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.kamill7779.qforge.gaokaocorpus.client.GaokaoAnalysisClient;
import io.github.kamill7779.qforge.gaokaocorpus.dto.IngestSessionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSession;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSourceFile;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSessionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSourceFileMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public IngestSessionDTO createSession(String operatorUser) {
        GkIngestSession session = new GkIngestSession();
        session.setSessionUuid(UUID.randomUUID().toString());
        session.setStatus("UPLOADED");
        session.setSourceKind("PDF");
        session.setSubjectCode("MATH");
        session.setOperatorUser(operatorUser);
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        ingestSessionMapper.insert(session);
        log.info("Created ingest session: uuid={}, operator={}", session.getSessionUuid(), operatorUser);
        return toDTO(session);
    }

    @Override
    public IngestSessionDTO getSession(String sessionUuid) {
        GkIngestSession session = ingestSessionMapper.selectOne(
                new LambdaQueryWrapper<GkIngestSession>()
                        .eq(GkIngestSession::getSessionUuid, sessionUuid));
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionUuid);
        }
        IngestSessionDTO dto = toDTO(session);
        List<GkIngestSourceFile> files = ingestSourceFileMapper.selectList(
                new LambdaQueryWrapper<GkIngestSourceFile>()
                        .eq(GkIngestSourceFile::getSessionId, session.getId()));
        dto.setSourceFileUuids(files.stream()
                .map(GkIngestSourceFile::getSourceFileUuid)
                .collect(Collectors.toList()));
        return dto;
    }

    @Override
    public List<IngestSessionDTO> listSessions(String operatorUser) {
        List<GkIngestSession> sessions = ingestSessionMapper.selectList(
                new LambdaQueryWrapper<GkIngestSession>()
                        .eq(GkIngestSession::getOperatorUser, operatorUser)
                        .orderByDesc(GkIngestSession::getUpdatedAt));
        return sessions.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void triggerOcrSplit(String sessionUuid) {
        GkIngestSession session = ingestSessionMapper.selectOne(
                new LambdaQueryWrapper<GkIngestSession>()
                        .eq(GkIngestSession::getSessionUuid, sessionUuid));
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionUuid);
        }
        session.setStatus("OCRING");
        session.setUpdatedAt(LocalDateTime.now());
        ingestSessionMapper.updateById(session);
        log.info("Triggered OCR split for session: uuid={}", sessionUuid);

        // OCR processing would be async via MQ in production.
        // For now, transition to SPLIT_READY to allow the flow to continue.
        session.setStatus("SPLIT_READY");
        session.setUpdatedAt(LocalDateTime.now());
        ingestSessionMapper.updateById(session);
    }

    private IngestSessionDTO toDTO(GkIngestSession entity) {
        IngestSessionDTO dto = new IngestSessionDTO();
        dto.setSessionUuid(entity.getSessionUuid());
        dto.setStatus(entity.getStatus());
        dto.setSourceKind(entity.getSourceKind());
        dto.setSubjectCode(entity.getSubjectCode());
        dto.setOperatorUser(entity.getOperatorUser());
        dto.setPaperNameGuess(entity.getPaperNameGuess());
        dto.setExamYearGuess(entity.getExamYearGuess());
        dto.setProvinceCodeGuess(entity.getProvinceCodeGuess());
        dto.setErrorMsg(entity.getErrorMsg());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}
