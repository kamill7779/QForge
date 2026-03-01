package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.Tag;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagRepository extends BaseMapper<Tag> {

    default Optional<Tag> findSystemTagByCategoryAndCode(String categoryCode, String tagCode) {
        Tag tag = this.selectOne(
                Wrappers.<Tag>lambdaQuery()
                        .eq(Tag::getScope, "SYSTEM")
                        .eq(Tag::getOwnerUser, "")
                        .eq(Tag::getCategoryCode, categoryCode)
                        .eq(Tag::getTagCode, tagCode)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(tag);
    }

    default Optional<Tag> findUserTagByCategoryAndCode(String ownerUser, String categoryCode, String tagCode) {
        Tag tag = this.selectOne(
                Wrappers.<Tag>lambdaQuery()
                        .eq(Tag::getScope, "USER")
                        .eq(Tag::getOwnerUser, ownerUser)
                        .eq(Tag::getCategoryCode, categoryCode)
                        .eq(Tag::getTagCode, tagCode)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(tag);
    }

    default List<Tag> findSystemTagsByCategory(String categoryCode) {
        return this.selectList(
                Wrappers.<Tag>lambdaQuery()
                        .eq(Tag::getScope, "SYSTEM")
                        .eq(Tag::getOwnerUser, "")
                        .eq(Tag::getCategoryCode, categoryCode)
                        .orderByAsc(Tag::getTagName)
        );
    }

    default List<Tag> findByCategoryCodes(List<String> categoryCodes) {
        if (categoryCodes == null || categoryCodes.isEmpty()) {
            return List.of();
        }
        return this.selectList(
                Wrappers.<Tag>lambdaQuery()
                        .in(Tag::getCategoryCode, categoryCodes)
                        .orderByAsc(Tag::getCategoryCode, Tag::getTagName)
        );
    }

    default List<Tag> findByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return this.selectBatchIds(ids);
    }

    default Tag save(Tag entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
