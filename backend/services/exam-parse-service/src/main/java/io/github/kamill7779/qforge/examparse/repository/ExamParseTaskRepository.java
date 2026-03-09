package io.github.kamill7779.qforge.examparse.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.examparse.entity.ExamParseTask;
import java.util.List;
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

    default Optional<ExamParseTask> findByTaskUuidAndOwnerUser(String taskUuid, String ownerUser) {
        ExamParseTask task = this.selectOne(
                Wrappers.<ExamParseTask>lambdaQuery()
                        .eq(ExamParseTask::getTaskUuid, taskUuid)
                        .eq(ExamParseTask::getOwnerUser, ownerUser)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(task);
    }

    default List<ExamParseTask> findAllByOwnerUser(String ownerUser) {
        return this.selectList(
                Wrappers.<ExamParseTask>lambdaQuery()
                        .eq(ExamParseTask::getOwnerUser, ownerUser)
                        .orderByDesc(ExamParseTask::getCreatedAt)
        );
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
