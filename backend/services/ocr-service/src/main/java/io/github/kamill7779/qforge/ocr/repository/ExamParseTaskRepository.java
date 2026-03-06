package io.github.kamill7779.qforge.ocr.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.ocr.entity.ExamParseTask;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamParseTaskRepository extends BaseMapper<ExamParseTask> {

    default Optional<ExamParseTask> findByTaskUuid(String taskUuid) {
        ExamParseTask task = this.selectOne(
                Wrappers.<ExamParseTask>lambdaQuery()
                        .eq(ExamParseTask::getTaskUuid, taskUuid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(task);
    }

    default ExamParseTask save(ExamParseTask entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
