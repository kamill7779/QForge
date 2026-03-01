package io.github.kamill7779.qforge.ocr.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.ocr.entity.OcrTask;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OcrTaskRepository extends BaseMapper<OcrTask> {

    default Optional<OcrTask> findByTaskUuid(String taskUuid) {
        OcrTask task = this.selectOne(
                Wrappers.<OcrTask>lambdaQuery()
                        .eq(OcrTask::getTaskUuid, taskUuid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(task);
    }

    default OcrTask save(OcrTask entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
