package io.github.kamill7779.qforge.exam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.exam.entity.QuestionType;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionTypeRepository extends BaseMapper<QuestionType> {

    default List<QuestionType> findAvailableForUser(String ownerUser) {
        return this.selectList(
                Wrappers.<QuestionType>lambdaQuery()
                        .eq(QuestionType::getEnabled, true)
                        .and(w -> w
                                .eq(QuestionType::getOwnerUser, "")
                                .or()
                                .eq(QuestionType::getOwnerUser, ownerUser))
                        .orderByAsc(QuestionType::getSortOrder)
        );
    }

    default QuestionType findByCodeAndOwner(String typeCode, String ownerUser) {
        return this.selectOne(
                Wrappers.<QuestionType>lambdaQuery()
                        .eq(QuestionType::getTypeCode, typeCode)
                        .eq(QuestionType::getOwnerUser, ownerUser)
                        .last("LIMIT 1")
        );
    }

    default QuestionType save(QuestionType entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
