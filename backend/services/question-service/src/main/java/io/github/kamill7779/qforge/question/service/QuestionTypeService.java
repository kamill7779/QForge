package io.github.kamill7779.qforge.question.service;

import io.github.kamill7779.qforge.question.dto.exam.QuestionTypeResponse;
import io.github.kamill7779.qforge.question.dto.exam.SaveQuestionTypeRequest;
import io.github.kamill7779.qforge.question.entity.QuestionType;
import io.github.kamill7779.qforge.question.exception.BusinessValidationException;
import io.github.kamill7779.qforge.question.repository.QuestionTypeRepository;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 题型配置服务 — 系统预置 + 用户自定义。
 */
@Service
public class QuestionTypeService {

    private static final Logger log = LoggerFactory.getLogger(QuestionTypeService.class);

    private final QuestionTypeRepository questionTypeRepository;

    public QuestionTypeService(QuestionTypeRepository questionTypeRepository) {
        this.questionTypeRepository = questionTypeRepository;
    }

    /**
     * 查询当前用户可见的所有题型（系统 + 自定义）。
     */
    public List<QuestionTypeResponse> listForUser(String requestUser) {
        List<QuestionType> types = questionTypeRepository.findAvailableForUser(requestUser);
        return types.stream().map(this::toResponse).toList();
    }

    /**
     * 创建用户自定义题型。
     */
    public QuestionTypeResponse createCustom(SaveQuestionTypeRequest request, String requestUser) {
        if (request.getTypeCode() == null || request.getTypeCode().isBlank()) {
            throw new BusinessValidationException("QT_CODE_REQUIRED", "题型编码不能为空",
                    Map.of(), HttpStatus.BAD_REQUEST);
        }
        if (request.getTypeLabel() == null || request.getTypeLabel().isBlank()) {
            throw new BusinessValidationException("QT_LABEL_REQUIRED", "题型名称不能为空",
                    Map.of(), HttpStatus.BAD_REQUEST);
        }

        // 检查同名冲突
        QuestionType existing = questionTypeRepository.findByCodeAndOwner(request.getTypeCode(), requestUser);
        if (existing != null) {
            throw new BusinessValidationException("QT_CODE_DUPLICATE", "该题型编码已存在",
                    Map.of("typeCode", request.getTypeCode()), HttpStatus.CONFLICT);
        }

        QuestionType entity = new QuestionType();
        entity.setTypeCode(request.getTypeCode().toUpperCase());
        entity.setTypeLabel(request.getTypeLabel());
        entity.setOwnerUser(requestUser);
        entity.setXmlHint(request.getXmlHint());
        entity.setSortOrder(request.getSortOrder() != null ? request.getSortOrder() : 100);
        entity.setEnabled(true);

        questionTypeRepository.save(entity);
        log.info("User [{}] created custom question type: {}", requestUser, entity.getTypeCode());
        return toResponse(entity);
    }

    /**
     * 更新用户自定义题型。
     */
    public QuestionTypeResponse updateCustom(Long id, SaveQuestionTypeRequest request, String requestUser) {
        QuestionType entity = questionTypeRepository.selectById(id);
        if (entity == null || !entity.getOwnerUser().equals(requestUser)) {
            throw new BusinessValidationException("QT_NOT_FOUND", "题型不存在或无权修改",
                    Map.of(), HttpStatus.NOT_FOUND);
        }
        if (entity.getOwnerUser().isEmpty()) {
            throw new BusinessValidationException("QT_SYSTEM_READONLY", "系统预置题型不可修改",
                    Map.of(), HttpStatus.FORBIDDEN);
        }

        if (request.getTypeLabel() != null && !request.getTypeLabel().isBlank()) {
            entity.setTypeLabel(request.getTypeLabel());
        }
        if (request.getXmlHint() != null) {
            entity.setXmlHint(request.getXmlHint());
        }
        if (request.getSortOrder() != null) {
            entity.setSortOrder(request.getSortOrder());
        }

        questionTypeRepository.save(entity);
        return toResponse(entity);
    }

    /**
     * 删除用户自定义题型（物理删除）。
     */
    public void deleteCustom(Long id, String requestUser) {
        QuestionType entity = questionTypeRepository.selectById(id);
        if (entity == null || !entity.getOwnerUser().equals(requestUser)) {
            throw new BusinessValidationException("QT_NOT_FOUND", "题型不存在或无权删除",
                    Map.of(), HttpStatus.NOT_FOUND);
        }
        if (entity.getOwnerUser().isEmpty()) {
            throw new BusinessValidationException("QT_SYSTEM_READONLY", "系统预置题型不可删除",
                    Map.of(), HttpStatus.FORBIDDEN);
        }
        questionTypeRepository.deleteById(id);
    }

    private QuestionTypeResponse toResponse(QuestionType e) {
        return new QuestionTypeResponse(
                e.getId(),
                e.getTypeCode(),
                e.getTypeLabel(),
                e.getOwnerUser(),
                e.getXmlHint(),
                e.getSortOrder() != null ? e.getSortOrder() : 0,
                Boolean.TRUE.equals(e.getEnabled()),
                e.getOwnerUser() == null || e.getOwnerUser().isEmpty()
        );
    }
}
