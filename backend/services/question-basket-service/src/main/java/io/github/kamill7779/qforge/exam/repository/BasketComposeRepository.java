package io.github.kamill7779.qforge.exam.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.exam.entity.BasketCompose;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BasketComposeRepository extends BaseMapper<BasketCompose> {

    default Optional<BasketCompose> findByOwnerUser(String ownerUser) {
        BasketCompose compose = this.selectOne(
                Wrappers.<BasketCompose>lambdaQuery()
                        .eq(BasketCompose::getOwnerUser, ownerUser)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(compose);
    }

    default BasketCompose save(BasketCompose entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
