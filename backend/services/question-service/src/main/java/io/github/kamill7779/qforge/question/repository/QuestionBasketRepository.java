package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.QuestionBasket;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

/**
 * 试题篮数据访问层。
 */
@Mapper
public interface QuestionBasketRepository extends BaseMapper<QuestionBasket> {

    /**
     * 查询用户篮中所有题目（按添加时间正序）。
     */
    default List<QuestionBasket> findByOwnerUser(String ownerUser) {
        return this.selectList(
                Wrappers.<QuestionBasket>lambdaQuery()
                        .eq(QuestionBasket::getOwnerUser, ownerUser)
                        .orderByAsc(QuestionBasket::getAddedAt)
        );
    }

    /**
     * 查询某题是否已在篮中。
     */
    default Optional<QuestionBasket> findByOwnerAndQuestionUuid(String ownerUser, String questionUuid) {
        QuestionBasket item = this.selectOne(
                Wrappers.<QuestionBasket>lambdaQuery()
                        .eq(QuestionBasket::getOwnerUser, ownerUser)
                        .eq(QuestionBasket::getQuestionUuid, questionUuid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(item);
    }

    /**
     * 清空用户篮。
     */
    default void deleteByOwnerUser(String ownerUser) {
        this.delete(
                Wrappers.<QuestionBasket>lambdaQuery()
                        .eq(QuestionBasket::getOwnerUser, ownerUser)
        );
    }

    /**
     * 统计用户篮中题目数。
     */
    default long countByOwnerUser(String ownerUser) {
        return this.selectCount(
                Wrappers.<QuestionBasket>lambdaQuery()
                        .eq(QuestionBasket::getOwnerUser, ownerUser)
        );
    }
}
