package io.github.kamill7779.qforge.persist.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.persist.entity.OcrTask;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OcrTaskRepository extends BaseMapper<OcrTask> {

    default Optional<OcrTask> findByTaskUuid(String taskUuid) {
        return Optional.ofNullable(
                selectOne(Wrappers.<OcrTask>lambdaQuery()
                        .eq(OcrTask::getTaskUuid, taskUuid)));
    }

    default OcrTask save(OcrTask entity) {
        if (entity.getId() == null) { insert(entity); } else { updateById(entity); }
        return entity;
    }
}
