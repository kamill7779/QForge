package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.QuestionAiTask;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionAiTaskRepository extends BaseMapper<QuestionAiTask> {

    default Optional<QuestionAiTask> findByTaskUuid(String taskUuid) {
        QuestionAiTask task = this.selectOne(
                Wrappers.<QuestionAiTask>lambdaQuery()
                        .eq(QuestionAiTask::getTaskUuid, taskUuid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(task);
    }

    default List<QuestionAiTask> findByQuestionUuid(String questionUuid) {
        return this.selectList(
                Wrappers.<QuestionAiTask>lambdaQuery()
                        .eq(QuestionAiTask::getQuestionUuid, questionUuid)
                        .orderByDesc(QuestionAiTask::getId)
        );
    }

    default Optional<QuestionAiTask> findLatestByQuestionUuid(String questionUuid) {
        QuestionAiTask task = this.selectOne(
                Wrappers.<QuestionAiTask>lambdaQuery()
                        .eq(QuestionAiTask::getQuestionUuid, questionUuid)
                        .orderByDesc(QuestionAiTask::getId)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(task);
    }

    default int deleteByQuestionUuid(String questionUuid) {
        return this.delete(
                Wrappers.<QuestionAiTask>lambdaQuery()
                        .eq(QuestionAiTask::getQuestionUuid, questionUuid)
        );
    }
}
