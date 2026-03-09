package io.github.kamill7779.qforge.exam.service;

import feign.Response;
import io.github.kamill7779.qforge.internal.api.QuestionCoreClient;
import io.github.kamill7779.qforge.internal.api.QuestionFullDTO;
import io.github.kamill7779.qforge.exam.client.ExportSidecarClient;
import io.github.kamill7779.qforge.exam.dto.ExportWordRequest;
import io.github.kamill7779.qforge.exam.dto.export.ExportAnswerPayload;
import io.github.kamill7779.qforge.exam.dto.export.ExportAssetPayload;
import io.github.kamill7779.qforge.exam.dto.export.ExportQuestionPayload;
import io.github.kamill7779.qforge.exam.dto.export.ExportSectionPayload;
import io.github.kamill7779.qforge.exam.dto.export.ExportSidecarRequest;
import io.github.kamill7779.qforge.exam.exception.BusinessValidationException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 导出服务 — 通过 QuestionCoreClient Feign 获取完整题目数据,
 * 然后调用 export-sidecar 生成 Word。
 */
@Service
public class ExportService {

    private static final Logger log = LoggerFactory.getLogger(ExportService.class);

    private final QuestionCoreClient questionCoreClient;
    private final ExportSidecarClient exportSidecarClient;

    public ExportService(
            QuestionCoreClient questionCoreClient,
            ExportSidecarClient exportSidecarClient
    ) {
        this.questionCoreClient = questionCoreClient;
        this.exportSidecarClient = exportSidecarClient;
    }

    public byte[] exportQuestionsWord(ExportWordRequest request, String requestUser) {
        // 1. 收集所有 UUID
        List<String> allUuids = collectAllUuids(request);
        if (allUuids.isEmpty()) {
            throw new BusinessValidationException(
                    "EXPORT_NO_QUESTIONS", "没有指定要导出的题目",
                    Map.of(), HttpStatus.BAD_REQUEST);
        }

        // 2. Feign 调用 question-core-service 批量获取完整题目数据
        List<QuestionFullDTO> fullQuestions;
        try {
            fullQuestions = questionCoreClient.batchGetFull(
                    Map.of("uuids", allUuids, "ownerUser", requestUser));
        } catch (Exception e) {
            log.error("Failed to fetch question data from question-core-service", e);
            throw new BusinessValidationException(
                    "EXPORT_CORE_UNAVAILABLE", "题目数据服务不可用: " + e.getMessage(),
                    Map.of(), HttpStatus.BAD_GATEWAY);
        }

        if (fullQuestions == null || fullQuestions.isEmpty()) {
            throw new BusinessValidationException(
                    "EXPORT_QUESTIONS_NOT_FOUND", "未找到任何题目",
                    Map.of("requestUser", requestUser), HttpStatus.NOT_FOUND);
        }

        Map<String, QuestionFullDTO> uuidToFull = fullQuestions.stream()
                .collect(Collectors.toMap(QuestionFullDTO::questionUuid, q -> q));

        // 3. 组装 ExportQuestionPayload
        List<ExportQuestionPayload> payloads = new ArrayList<>();
        for (String uuid : allUuids) {
            QuestionFullDTO f = uuidToFull.get(uuid);
            if (f == null) continue;

            List<ExportAssetPayload> stemAssets = f.stemAssets() != null
                    ? f.stemAssets().stream()
                        .map(a -> new ExportAssetPayload(a.refKey(), a.imageData(), a.mimeType()))
                        .toList()
                    : List.of();

            List<ExportAnswerPayload> ansPayloads = f.answers() != null
                    ? f.answers().stream()
                        .map(a -> {
                            List<ExportAssetPayload> ansAssets = a.assets() != null
                                    ? a.assets().stream()
                                        .map(aa -> new ExportAssetPayload(aa.refKey(), aa.imageData(), aa.mimeType()))
                                        .toList()
                                    : List.of();
                            return new ExportAnswerPayload(a.answerUuid(), a.latexText(), a.sortOrder(), ansAssets);
                        })
                        .toList()
                    : List.of();

            List<Map<String, String>> mainTags = f.mainTags() != null
                    ? f.mainTags().stream()
                        .map(t -> Map.of(
                                "categoryCode", t.categoryCode() != null ? t.categoryCode() : "",
                                "categoryName", t.categoryName() != null ? t.categoryName() : "",
                                "tagName", t.tagName() != null ? t.tagName() : ""
                        ))
                        .toList()
                    : List.of();

            payloads.add(new ExportQuestionPayload(
                    f.questionUuid(),
                    f.stemText(),
                    f.difficulty() != null ? f.difficulty().doubleValue() : null,
                    ansPayloads,
                    stemAssets,
                    mainTags,
                    f.secondaryTags() != null ? f.secondaryTags() : List.of()
            ));
        }

        // 4. 构建 sidecar 请求
        List<ExportSectionPayload> sectionPayloads = null;
        if (request.sections() != null && !request.sections().isEmpty()) {
            sectionPayloads = request.sections().stream()
                    .map(s -> new ExportSectionPayload(s.title(), s.questionUuids()))
                    .toList();
        }

        ExportSidecarRequest sidecarRequest = new ExportSidecarRequest(
                payloads,
                request.safeTitle(),
                request.includeAnswers(),
                request.safeAnswerPosition(),
                sectionPayloads
        );

        // 5. 调用 export-sidecar
        try {
            Response feignResponse = exportSidecarClient.exportQuestionsWord(sidecarRequest);
            if (feignResponse.status() != 200) {
                log.error("export-sidecar returned status {}", feignResponse.status());
                throw new BusinessValidationException(
                        "EXPORT_SIDECAR_ERROR", "导出服务返回错误: " + feignResponse.status(),
                        Map.of(), HttpStatus.BAD_GATEWAY);
            }
            return feignResponse.body().asInputStream().readAllBytes();
        } catch (BusinessValidationException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to read export-sidecar response", e);
            throw new BusinessValidationException(
                    "EXPORT_IO_ERROR", "读取导出文件失败",
                    Map.of(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (Exception e) {
            log.error("export-sidecar call failed", e);
            throw new BusinessValidationException(
                    "EXPORT_SIDECAR_UNAVAILABLE", "导出服务不可用: " + e.getMessage(),
                    Map.of(), HttpStatus.BAD_GATEWAY);
        }
    }

    private List<String> collectAllUuids(ExportWordRequest request) {
        if (request.sections() != null && !request.sections().isEmpty()) {
            List<String> uuids = new ArrayList<>();
            for (ExportSectionPayload s : request.sections()) {
                uuids.addAll(s.questionUuids());
            }
            return uuids;
        }
        return request.questionUuids() != null ? request.questionUuids() : List.of();
    }
}
