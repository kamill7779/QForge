package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.QuestionOcrTask;
import java.util.Collection;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionOcrTaskRepository extends BaseMapper<QuestionOcrTask> {

    default Optional<QuestionOcrTask> findByTaskUuid(String taskUuid) {
        QuestionOcrTask task = this.selectOne(
                Wrappers.<QuestionOcrTask>lambdaQuery()
                        .eq(QuestionOcrTask::getTaskUuid, taskUuid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(task);
    }

    default QuestionOcrTask save(QuestionOcrTask entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }

    default int deleteByQuestionUuid(String questionUuid) {
        return this.delete(
                Wrappers.<QuestionOcrTask>lambdaQuery()
                        .eq(QuestionOcrTask::getQuestionUuid, questionUuid)
        );
    }

    default boolean existsByQuestionUuidAndBizTypeAndStatusIn(
            String questionUuid,
            String bizType,
            Collection<String> statuses
    ) {
        if (statuses == null || statuses.isEmpty()) {
            return false;
        }
        Long count = this.selectCount(
                Wrappers.<QuestionOcrTask>lambdaQuery()
                        .eq(QuestionOcrTask::getQuestionUuid, questionUuid)
                        .eq(QuestionOcrTask::getBizType, bizType)
                        .in(QuestionOcrTask::getStatus, statuses)
        );
        return count != null && count > 0;
    }
}
