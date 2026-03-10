package io.github.kamill7779.qforge.exam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.exam.entity.BasketComposeSection;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BasketComposeSectionRepository extends BaseMapper<BasketComposeSection> {

    default List<BasketComposeSection> findByComposeId(Long composeId) {
        return this.selectList(
                Wrappers.<BasketComposeSection>lambdaQuery()
                        .eq(BasketComposeSection::getComposeId, composeId)
                        .orderByAsc(BasketComposeSection::getSortOrder)
        );
    }

    default Optional<BasketComposeSection> findLastSection(Long composeId) {
        BasketComposeSection section = this.selectOne(
                Wrappers.<BasketComposeSection>lambdaQuery()
                        .eq(BasketComposeSection::getComposeId, composeId)
                        .orderByDesc(BasketComposeSection::getSortOrder)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(section);
    }

    default void deleteByComposeId(Long composeId) {
        this.delete(
                Wrappers.<BasketComposeSection>lambdaQuery()
                        .eq(BasketComposeSection::getComposeId, composeId)
        );
    }

    default BasketComposeSection save(BasketComposeSection entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
