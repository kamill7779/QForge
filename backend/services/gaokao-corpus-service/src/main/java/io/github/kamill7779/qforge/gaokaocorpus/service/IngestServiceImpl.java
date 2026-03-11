package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.kamill7779.qforge.internal.api.GaokaoSplitClient;
import io.github.kamill7779.qforge.internal.api.GaokaoSplitRequest;
import io.github.kamill7779.qforge.internal.api.GaokaoSplitResponse;
import io.github.kamill7779.qforge.gaokaocorpus.client.GaokaoAnalysisClient;
import io.github.kamill7779.qforge.gaokaocorpus.dto.IngestSessionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestOcrPage;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSession;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSplitQuestion;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSourceFile;
import io.github.kamill7779.qforge.gaokaocorpus.exception.BusinessValidationException;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestOcrPageMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSessionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSplitQuestionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSourceFileMapper;
import io.github.kamill7779.qforge.storage.QForgeStorageService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Locale;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IngestServiceImpl implements IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestServiceImpl.class);
    private static final String OCR_LOCK_PREFIX = "qforge:gaokao:ingest:ocr:";

    private final GkIngestSessionMapper ingestSessionMapper;
    private final GkIngestSourceFileMapper ingestSourceFileMapper;
    private final GkIngestOcrPageMapper ingestOcrPageMapper;
    private final GkIngestSplitQuestionMapper ingestSplitQuestionMapper;
    private final GaokaoAnalysisClient analysisClient;
    private final GaokaoSplitClient gaokaoSplitClient;
    private final QForgeStorageService storageService;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final int maxUploadFiles;
    private final Set<String> allowedExtensions;

    public IngestServiceImpl(
            GkIngestSessionMapper ingestSessionMapper,
            GkIngestSourceFileMapper ingestSourceFileMapper,
            GkIngestOcrPageMapper ingestOcrPageMapper,
            GkIngestSplitQuestionMapper ingestSplitQuestionMapper,
            GaokaoAnalysisClient analysisClient,
            GaokaoSplitClient gaokaoSplitClient,
            QForgeStorageService storageService,
            StringRedisTemplate stringRedisTemplate,
            ObjectMapper objectMapper,
            @org.springframework.beans.factory.annotation.Value("${qforge.gaokao.max-upload-files:20}") int maxUploadFiles,
            @org.springframework.beans.factory.annotation.Value("${qforge.gaokao.allowed-extensions:pdf,jpg,jpeg,png}") String allowedExtensions
    ) {
        this.ingestSessionMapper = ingestSessionMapper;
        this.ingestSourceFileMapper = ingestSourceFileMapper;
        this.ingestOcrPageMapper = ingestOcrPageMapper;
        this.ingestSplitQuestionMapper = ingestSplitQuestionMapper;
        this.analysisClient = analysisClient;
        this.gaokaoSplitClient = gaokaoSplitClient;
        this.storageService = storageService;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.maxUploadFiles = maxUploadFiles;
        this.allowedExtensions = Arrays.stream(allowedExtensions.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toSet());
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
    public void uploadFiles(String sessionUuid, MultipartFile[] files) {
        GkIngestSession session = ingestSessionMapper.selectOne(
                new LambdaQueryWrapper<GkIngestSession>()
                        .eq(GkIngestSession::getSessionUuid, sessionUuid));
        if (session == null) {
            throw new IllegalArgumentException("Session not found: " + sessionUuid);
        }
        if (files == null || files.length == 0) {
            throw new BusinessValidationException(
                    "GAOKAO_UPLOAD_EMPTY",
                    "At least one source file is required",
                    HttpStatus.BAD_REQUEST);
        }
        if (files.length > maxUploadFiles) {
            throw new BusinessValidationException(
                    "GAOKAO_UPLOAD_TOO_MANY_FILES",
                    "Too many files uploaded in one request",
                    HttpStatus.BAD_REQUEST,
                    java.util.Map.of("maxUploadFiles", maxUploadFiles));
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                throw new BusinessValidationException(
                        "GAOKAO_UPLOAD_EMPTY_FILE",
                        "Uploaded file must not be empty",
                        HttpStatus.BAD_REQUEST);
            }

            String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename());
            String extension = extractExtension(originalFilename);
            validateExtension(extension, originalFilename);

            String sourceFileUuid = UUID.randomUUID().toString();

            try {
                byte[] content = file.getBytes();
                String objectKey = storageService.buildObjectKey(
                        "gaokao/ingest",
                        sessionUuid,
                        sourceFileUuid + (extension.isEmpty() ? "" : "." + extension)
                );
                String storageRef = storageService.putObject(
                        objectKey,
                        new java.io.ByteArrayInputStream(content),
                        content.length,
                        file.getContentType()
                );

                GkIngestSourceFile sourceFile = new GkIngestSourceFile();
                sourceFile.setSourceFileUuid(sourceFileUuid);
                sourceFile.setSessionId(session.getId());
                sourceFile.setFileName(originalFilename);
                sourceFile.setFileType(toFileType(extension));
                sourceFile.setStorageRef(storageRef);
                sourceFile.setPageCount(1);
                sourceFile.setChecksumSha256(calculateSha256(content));
                sourceFile.setCreatedAt(LocalDateTime.now());
                ingestSourceFileMapper.insert(sourceFile);
            } catch (IOException e) {
                throw new RuntimeException("Failed to persist uploaded file: " + originalFilename, e);
            }
        }

        session.setSourceKind(resolveSourceKind(files));
        session.setStatus("UPLOADED");
        session.setUpdatedAt(LocalDateTime.now());
        ingestSessionMapper.updateById(session);
        log.info("Uploaded {} files for ingest session={}", files.length, sessionUuid);
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
        if ("SPLIT_READY".equals(session.getStatus())) {
            log.info("Skip OCR trigger because session is already split-ready: uuid={}", sessionUuid);
            return;
        }
        if ("OCRING".equals(session.getStatus())) {
            throw new BusinessValidationException(
                    "GAOKAO_OCR_ALREADY_RUNNING",
                    "OCR is already running for session: " + sessionUuid,
                    HttpStatus.CONFLICT);
        }
        if (!"UPLOADED".equals(session.getStatus()) && !"OCR_FAILED".equals(session.getStatus())) {
            throw new BusinessValidationException(
                    "GAOKAO_OCR_INVALID_STATUS",
                    "Session status does not allow OCR trigger: " + session.getStatus(),
                    HttpStatus.CONFLICT);
        }
        acquireOcrLock(sessionUuid);
        boolean locked = true;
        try {
            int updated = ingestSessionMapper.update(
                    null,
                    new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<GkIngestSession>()
                            .eq(GkIngestSession::getId, session.getId())
                            .eq(GkIngestSession::getStatus, session.getStatus())
                            .set(GkIngestSession::getStatus, "OCRING")
                            .set(GkIngestSession::getUpdatedAt, LocalDateTime.now())
            );
            if (updated == 0) {
                throw new BusinessValidationException(
                        "GAOKAO_OCR_STATUS_CONFLICT",
                        "Session status changed before OCR trigger: " + sessionUuid,
                        HttpStatus.CONFLICT);
            }
        log.info("Triggered OCR split for session: uuid={}", sessionUuid);

        List<GkIngestSourceFile> sourceFiles = ingestSourceFileMapper.selectList(
                new LambdaQueryWrapper<GkIngestSourceFile>()
                        .eq(GkIngestSourceFile::getSessionId, session.getId())
                        .orderByAsc(GkIngestSourceFile::getId));
        ingestOcrPageMapper.delete(new LambdaQueryWrapper<GkIngestOcrPage>()
                .eq(GkIngestOcrPage::getSessionId, session.getId()));
        ingestSplitQuestionMapper.delete(new LambdaQueryWrapper<GkIngestSplitQuestion>()
            .eq(GkIngestSplitQuestion::getSessionId, session.getId()));

        try {
            GaokaoSplitRequest splitRequest = new GaokaoSplitRequest(
                sourceFiles.stream()
                    .map(sourceFile -> new GaokaoSplitRequest.SourceFileEntry(
                        sourceFile.getId() != null ? sourceFile.getId().intValue() : 0,
                        sourceFile.getFileName(),
                        sourceFile.getFileType(),
                        encodeFileAsBase64(sourceFile.getStorageRef()),
                        sourceFile.getStorageRef()))
                    .collect(Collectors.toList()),
                true);
            GaokaoSplitResponse response = gaokaoSplitClient.splitExam(splitRequest);
            LocalDateTime now = LocalDateTime.now();
            if (response != null && response.questions() != null) {
            for (GaokaoSplitResponse.SplitQuestionEntry entry : response.questions()) {
                GkIngestSplitQuestion question = new GkIngestSplitQuestion();
                question.setSessionId(session.getId());
                question.setSeq(entry.seq());
                question.setQuestionTypeCode(entry.questionTypeCode());
                question.setSourcePagesJson(writeJson(entry.sourcePages()));
                question.setRawStemText(entry.rawStemText());
                question.setStemXml(entry.stemXml());
                question.setRawAnswerText(entry.rawAnswerText());
                question.setAnswerXml(entry.answerXml());
                question.setStemImageRefsJson(writeJson(entry.stemImageRefs()));
                question.setAnswerImageRefsJson(writeJson(entry.answerImageRefs()));
                question.setStemImagesJson(writeJson(entry.stemImages()));
                question.setAnswerImagesJson(writeJson(entry.answerImages()));
                question.setParseError(entry.parseError());
                question.setErrorMsg(entry.errorMsg());
                question.setCreatedAt(now);
                ingestSplitQuestionMapper.insert(question);
            }
            }
            session.setStatus("SPLIT_READY");
            session.setErrorMsg(null);
        } catch (Exception ex) {
            session.setStatus("OCR_FAILED");
            session.setErrorMsg(ex.getMessage());
            log.error("OCR split failed for session={}", sessionUuid, ex);
        }
        session.setUpdatedAt(LocalDateTime.now());
        ingestSessionMapper.updateById(session);
        } finally {
            if (locked) {
                releaseOcrLock(sessionUuid);
            }
        }
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

    private String sanitizeOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "upload.bin";
        }
        return Path.of(originalFilename).getFileName().toString();
    }

    private String extractExtension(String filename) {
        int lastDot = filename.lastIndexOf('.');
        if (lastDot < 0 || lastDot == filename.length() - 1) {
            return "";
        }
        return filename.substring(lastDot + 1).toLowerCase(Locale.ROOT);
    }

    private void validateExtension(String extension, String originalFilename) {
        if (!allowedExtensions.contains(extension)) {
            throw new BusinessValidationException(
                    "GAOKAO_UPLOAD_INVALID_EXTENSION",
                    "Unsupported file type: " + originalFilename,
                    HttpStatus.BAD_REQUEST,
                    java.util.Map.of("allowedExtensions", allowedExtensions));
        }
    }

    private String toFileType(String extension) {
        return switch (extension) {
            case "pdf" -> "PDF";
            case "png" -> "PNG";
            case "jpg", "jpeg" -> "JPG";
            default -> extension.toUpperCase(Locale.ROOT);
        };
    }

    private String resolveSourceKind(MultipartFile[] files) {
        boolean containsPdf = Stream.of(files)
                .map(file -> extractExtension(sanitizeOriginalFilename(file.getOriginalFilename())))
                .anyMatch("pdf"::equals);
        return containsPdf ? "PDF" : "IMAGE_SET";
    }

    private String calculateSha256(Path filePath) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(filePath);
                 DigestInputStream dis = new DigestInputStream(in, digest)) {
                byte[] buf = new byte[8192];
                while (dis.read(buf) != -1) { /* drain */ }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate sha256 for file: " + filePath, e);
        }
    }

    private String calculateSha256(byte[] content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to calculate sha256 for uploaded content", e);
        }
    }

    private String encodeFileAsBase64(String storageRef) {
        if (storageRef != null && storageRef.startsWith("cos://")) {
            try {
                return storageService.getObjectBase64(storageRef);
            } catch (IOException ex) {
                throw new RuntimeException("Failed to read COS source file for OCR: " + storageRef, ex);
            }
        }
        try (InputStream in = Files.newInputStream(Path.of(storageRef));
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            OutputStream base64Out = Base64.getEncoder().wrap(baos);
            byte[] buf = new byte[8192];
            int read;
            while ((read = in.read(buf)) != -1) {
                base64Out.write(buf, 0, read);
            }
            base64Out.close();
            return baos.toString(java.nio.charset.StandardCharsets.ISO_8859_1);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to read source file for OCR: " + storageRef, ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Failed to serialize split result JSON", ex);
        }
    }

    private void acquireOcrLock(String sessionUuid) {
        if (stringRedisTemplate == null) {
            return;
        }
        Boolean locked = stringRedisTemplate.opsForValue().setIfAbsent(
                OCR_LOCK_PREFIX + sessionUuid,
                "1",
                java.time.Duration.ofMinutes(10)
        );
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessValidationException(
                    "GAOKAO_OCR_LOCKED",
                    "OCR trigger is already in progress for session: " + sessionUuid,
                    HttpStatus.CONFLICT);
        }
    }

    private void releaseOcrLock(String sessionUuid) {
        if (stringRedisTemplate != null) {
            stringRedisTemplate.delete(OCR_LOCK_PREFIX + sessionUuid);
        }
    }
}
