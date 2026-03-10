package io.github.kamill7779.qforge.exam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.exam.entity.QuestionBasket;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionBasketRepository extends BaseMapper<QuestionBasket> {

    default List<QuestionBasket> findByOwnerUser(String ownerUser) {
        return this.selectList(
                Wrappers.<QuestionBasket>lambdaQuery()
                        .eq(QuestionBasket::getOwnerUser, ownerUser)
                        .orderByAsc(QuestionBasket::getAddedAt)
        );
    }

    default Optional<QuestionBasket> findByOwnerAndQuestionUuid(String ownerUser, String questionUuid) {
        QuestionBasket item = this.selectOne(
                Wrappers.<QuestionBasket>lambdaQuery()
                        .eq(QuestionBasket::getOwnerUser, ownerUser)
                        .eq(QuestionBasket::getQuestionUuid, questionUuid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(item);
    }

    default void deleteByOwnerUser(String ownerUser) {
        this.delete(
                Wrappers.<QuestionBasket>lambdaQuery()
                        .eq(QuestionBasket::getOwnerUser, ownerUser)
        );
    }

    default long countByOwnerUser(String ownerUser) {
        return this.selectCount(
                Wrappers.<QuestionBasket>lambdaQuery()
                        .eq(QuestionBasket::getOwnerUser, ownerUser)
        );
    }
}
