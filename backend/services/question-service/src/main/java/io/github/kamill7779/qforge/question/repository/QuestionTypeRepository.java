package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.QuestionType;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionTypeRepository extends BaseMapper<QuestionType> {

    /**
     * 查询系统预置 + 指定用户的自定义题型（已启用）。
     */
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

    /**
     * 按 type_code + owner_user 查找唯一记录。
     */
    default QuestionType findByCodeAndOwner(String typeCode, String ownerUser) {
        return this.selectOne(
                Wrappers.<QuestionType>lambdaQuery()
                        .eq(QuestionType::getTypeCode, typeCode)
                        .eq(QuestionType::getOwnerUser, ownerUser)
                        .last("LIMIT 1")
        );
    }

    /**
     * 查询指定用户的自定义题型。
     */
    default List<QuestionType> findCustomByUser(String ownerUser) {
        return this.selectList(
                Wrappers.<QuestionType>lambdaQuery()
                        .eq(QuestionType::getOwnerUser, ownerUser)
                        .orderByAsc(QuestionType::getSortOrder)
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
