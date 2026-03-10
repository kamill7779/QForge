package io.github.kamill7779.qforge.gaokaocorpus.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.kamill7779.qforge.gaokaocorpus.client.GaokaoAnalysisClient;
import io.github.kamill7779.qforge.gaokaocorpus.client.OcrRecognizeRequest;
import io.github.kamill7779.qforge.gaokaocorpus.client.OcrRecognizeResponse;
import io.github.kamill7779.qforge.gaokaocorpus.client.OcrServiceClient;
import io.github.kamill7779.qforge.gaokaocorpus.dto.IngestSessionDTO;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestOcrPage;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSession;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkIngestSourceFile;
import io.github.kamill7779.qforge.gaokaocorpus.exception.BusinessValidationException;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestOcrPageMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSessionMapper;
import io.github.kamill7779.qforge.gaokaocorpus.repository.GkIngestSourceFileMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class IngestServiceImpl implements IngestService {

    private static final Logger log = LoggerFactory.getLogger(IngestServiceImpl.class);

    private final GkIngestSessionMapper ingestSessionMapper;
    private final GkIngestSourceFileMapper ingestSourceFileMapper;
    private final GkIngestOcrPageMapper ingestOcrPageMapper;
    private final GaokaoAnalysisClient analysisClient;
    private final OcrServiceClient ocrServiceClient;
    private final int maxUploadFiles;
    private final Set<String> allowedExtensions;
    private final Path uploadRootDir;

    public IngestServiceImpl(
            GkIngestSessionMapper ingestSessionMapper,
            GkIngestSourceFileMapper ingestSourceFileMapper,
            GkIngestOcrPageMapper ingestOcrPageMapper,
            GaokaoAnalysisClient analysisClient,
            OcrServiceClient ocrServiceClient,
            @Value("${qforge.gaokao.max-upload-files:20}") int maxUploadFiles,
            @Value("${qforge.gaokao.allowed-extensions:pdf,jpg,jpeg,png}") String allowedExtensions,
            @Value("${qforge.gaokao.upload-root-dir:./data/gaokao/uploads}") String uploadRootDir
    ) {
        this.ingestSessionMapper = ingestSessionMapper;
        this.ingestSourceFileMapper = ingestSourceFileMapper;
        this.ingestOcrPageMapper = ingestOcrPageMapper;
        this.analysisClient = analysisClient;
        this.ocrServiceClient = ocrServiceClient;
        this.maxUploadFiles = maxUploadFiles;
        this.allowedExtensions = Arrays.stream(allowedExtensions.split(","))
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .filter(item -> !item.isBlank())
                .collect(Collectors.toSet());
        this.uploadRootDir = Path.of(uploadRootDir).toAbsolutePath().normalize();
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

        Path sessionDir = uploadRootDir.resolve(sessionUuid);
        try {
            Files.createDirectories(sessionDir);
        } catch (IOException e) {
            throw new RuntimeException("Failed to prepare upload directory", e);
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
            Path targetPath = sessionDir.resolve(sourceFileUuid + (extension.isEmpty() ? "" : "." + extension));

            try {
                Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                throw new RuntimeException("Failed to persist uploaded file: " + originalFilename, e);
            }

            GkIngestSourceFile sourceFile = new GkIngestSourceFile();
            sourceFile.setSourceFileUuid(sourceFileUuid);
            sourceFile.setSessionId(session.getId());
            sourceFile.setFileName(originalFilename);
            sourceFile.setFileType(toFileType(extension));
            sourceFile.setStorageRef(targetPath.toString().replace('\\', '/'));
            sourceFile.setPageCount(1);
            sourceFile.setChecksumSha256(calculateSha256(targetPath));
            sourceFile.setCreatedAt(LocalDateTime.now());
            ingestSourceFileMapper.insert(sourceFile);
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
        session.setStatus("OCRING");
        session.setUpdatedAt(LocalDateTime.now());
        ingestSessionMapper.updateById(session);
        log.info("Triggered OCR split for session: uuid={}", sessionUuid);

        List<GkIngestSourceFile> sourceFiles = ingestSourceFileMapper.selectList(
                new LambdaQueryWrapper<GkIngestSourceFile>()
                        .eq(GkIngestSourceFile::getSessionId, session.getId())
                        .orderByAsc(GkIngestSourceFile::getId));
        ingestOcrPageMapper.delete(new LambdaQueryWrapper<GkIngestOcrPage>()
                .eq(GkIngestOcrPage::getSessionId, session.getId()));

        try {
            int pageNo = 1;
            for (GkIngestSourceFile sourceFile : sourceFiles) {
                OcrRecognizeRequest request = new OcrRecognizeRequest();
                request.setImageBase64(encodeFileAsBase64(sourceFile.getStorageRef()));
                request.setFileType(sourceFile.getFileType());
                OcrRecognizeResponse response = ocrServiceClient.recognize(request);

                GkIngestOcrPage page = new GkIngestOcrPage();
                page.setSessionId(session.getId());
                page.setSourceFileId(sourceFile.getId());
                page.setPageNo(pageNo++);
                page.setFullText(response != null ? response.getFullText() : "");
                page.setLayoutJson(response != null ? response.getLayoutJson() : "[]");
                page.setFormulaJson(response != null ? response.getFormulaJson() : "[]");
                page.setPageImageRef(sourceFile.getStorageRef());
                page.setCreatedAt(LocalDateTime.now());
                ingestOcrPageMapper.insert(page);
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

    private String encodeFileAsBase64(String storageRef) {
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
}
