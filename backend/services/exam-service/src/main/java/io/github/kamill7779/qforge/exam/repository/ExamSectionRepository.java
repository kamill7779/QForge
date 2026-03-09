package io.github.kamill7779.qforge.exam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.exam.entity.ExamSection;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamSectionRepository extends BaseMapper<ExamSection> {

    default List<ExamSection> findByPaperId(Long paperId) {
        return this.selectList(
                Wrappers.<ExamSection>lambdaQuery()
                        .eq(ExamSection::getPaperId, paperId)
                        .orderByAsc(ExamSection::getSortOrder)
        );
    }

    default List<ExamSection> findByPaperIds(List<Long> paperIds) {
        if (paperIds == null || paperIds.isEmpty()) {
            return List.of();
        }
        return this.selectList(
                Wrappers.<ExamSection>lambdaQuery()
                        .in(ExamSection::getPaperId, paperIds)
                        .orderByAsc(ExamSection::getPaperId, ExamSection::getSortOrder)
        );
    }

    default void deleteByPaperId(Long paperId) {
        this.delete(
                Wrappers.<ExamSection>lambdaQuery()
                        .eq(ExamSection::getPaperId, paperId)
        );
    }

    default ExamSection save(ExamSection entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
