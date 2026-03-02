package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.QuestionAsset;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionAssetRepository extends BaseMapper<QuestionAsset> {

    default Optional<QuestionAsset> findByAssetUuid(String assetUuid) {
        QuestionAsset asset = this.selectOne(
                Wrappers.<QuestionAsset>lambdaQuery()
                        .eq(QuestionAsset::getAssetUuid, assetUuid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(asset);
    }

    default List<QuestionAsset> findByQuestionId(Long questionId) {
        return this.selectList(
                Wrappers.<QuestionAsset>lambdaQuery()
                        .eq(QuestionAsset::getQuestionId, questionId)
        );
    }

    default List<QuestionAsset> findByQuestionIdAndAssetType(Long questionId, String assetType) {
        return this.selectList(
                Wrappers.<QuestionAsset>lambdaQuery()
                        .eq(QuestionAsset::getQuestionId, questionId)
                        .eq(QuestionAsset::getAssetType, assetType)
        );
    }

    default QuestionAsset save(QuestionAsset entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }

    default void softDeleteByQuestionId(Long questionId) {
        List<QuestionAsset> assets = findByQuestionId(questionId);
        for (QuestionAsset asset : assets) {
            this.deleteById(asset.getId());
        }
    }
}
