package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.ExamQuestion;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamQuestionRepository extends BaseMapper<ExamQuestion> {

    default List<ExamQuestion> findBySectionId(Long sectionId) {
        return this.selectList(
                Wrappers.<ExamQuestion>lambdaQuery()
                        .eq(ExamQuestion::getSectionId, sectionId)
                        .orderByAsc(ExamQuestion::getSortOrder)
        );
    }

    default List<ExamQuestion> findBySectionIds(List<Long> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            return List.of();
        }
        return this.selectList(
                Wrappers.<ExamQuestion>lambdaQuery()
                        .in(ExamQuestion::getSectionId, sectionIds)
                        .orderByAsc(ExamQuestion::getSortOrder)
        );
    }

    default void deleteBySectionIds(List<Long> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) return;
        this.delete(
                Wrappers.<ExamQuestion>lambdaQuery()
                        .in(ExamQuestion::getSectionId, sectionIds)
        );
    }

    default ExamQuestion save(ExamQuestion entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
