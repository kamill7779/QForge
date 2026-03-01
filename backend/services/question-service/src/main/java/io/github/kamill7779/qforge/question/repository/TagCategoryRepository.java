package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.TagCategory;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TagCategoryRepository extends BaseMapper<TagCategory> {

    default List<TagCategory> findEnabledCategories() {
        return this.selectList(
                Wrappers.<TagCategory>lambdaQuery()
                        .eq(TagCategory::isEnabled, true)
                        .orderByAsc(TagCategory::getSortOrder, TagCategory::getId)
        );
    }

    default List<TagCategory> findEnabledMainCategories() {
        return this.selectList(
                Wrappers.<TagCategory>lambdaQuery()
                        .eq(TagCategory::isEnabled, true)
                        .eq(TagCategory::getCategoryKind, "MAIN")
                        .orderByAsc(TagCategory::getSortOrder, TagCategory::getId)
        );
    }

    default Optional<TagCategory> findEnabledByCode(String categoryCode) {
        TagCategory category = this.selectOne(
                Wrappers.<TagCategory>lambdaQuery()
                        .eq(TagCategory::isEnabled, true)
                        .eq(TagCategory::getCategoryCode, categoryCode)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(category);
    }
}

