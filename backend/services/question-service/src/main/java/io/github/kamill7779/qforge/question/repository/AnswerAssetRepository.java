package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.AnswerAsset;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnswerAssetRepository extends BaseMapper<AnswerAsset> {

    default List<AnswerAsset> findByQuestionId(Long questionId) {
        return this.selectList(
                Wrappers.<AnswerAsset>lambdaQuery()
                        .eq(AnswerAsset::getQuestionId, questionId)
        );
    }

    default List<AnswerAsset> findByAnswerId(Long answerId) {
        return this.selectList(
                Wrappers.<AnswerAsset>lambdaQuery()
                        .eq(AnswerAsset::getAnswerId, answerId)
        );
    }

    default Optional<AnswerAsset> findByAnswerIdAndRefKey(Long answerId, String refKey) {
        AnswerAsset asset = this.selectOne(
                Wrappers.<AnswerAsset>lambdaQuery()
                        .eq(AnswerAsset::getAnswerId, answerId)
                        .eq(AnswerAsset::getRefKey, refKey)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(asset);
    }

    default AnswerAsset save(AnswerAsset entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }

    /**
     * 批量查询多个题目的所有答案图片资产。
     */
    default List<AnswerAsset> findByQuestionIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return this.selectList(
                Wrappers.<AnswerAsset>lambdaQuery()
                        .in(AnswerAsset::getQuestionId, questionIds)
        );
    }

    default void deleteByAnswerId(Long answerId) {
        List<AnswerAsset> assets = findByAnswerId(answerId);
        for (AnswerAsset asset : assets) {
            this.deleteById(asset.getId());
        }
    }
}

