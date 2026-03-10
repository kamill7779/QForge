package io.github.kamill7779.qforge.exam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.exam.entity.BasketComposeQuestion;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BasketComposeQuestionRepository extends BaseMapper<BasketComposeQuestion> {

    default List<BasketComposeQuestion> findBySectionIds(List<Long> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            return List.of();
        }
        return this.selectList(
                Wrappers.<BasketComposeQuestion>lambdaQuery()
                        .in(BasketComposeQuestion::getSectionId, sectionIds)
                        .orderByAsc(BasketComposeQuestion::getSectionId, BasketComposeQuestion::getSortOrder)
        );
    }

    default Optional<BasketComposeQuestion> findBySectionAndQuestionUuid(Long sectionId, String questionUuid) {
        BasketComposeQuestion question = this.selectOne(
                Wrappers.<BasketComposeQuestion>lambdaQuery()
                        .eq(BasketComposeQuestion::getSectionId, sectionId)
                        .eq(BasketComposeQuestion::getQuestionUuid, questionUuid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(question);
    }

    default void deleteBySectionIds(List<Long> sectionIds) {
        if (sectionIds == null || sectionIds.isEmpty()) {
            return;
        }
        this.delete(
                Wrappers.<BasketComposeQuestion>lambdaQuery()
                        .in(BasketComposeQuestion::getSectionId, sectionIds)
        );
    }

    default void deleteBySectionIdAndQuestionUuid(Long sectionId, String questionUuid) {
        this.delete(
                Wrappers.<BasketComposeQuestion>lambdaQuery()
                        .eq(BasketComposeQuestion::getSectionId, sectionId)
                        .eq(BasketComposeQuestion::getQuestionUuid, questionUuid)
        );
    }

    default BasketComposeQuestion save(BasketComposeQuestion entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
