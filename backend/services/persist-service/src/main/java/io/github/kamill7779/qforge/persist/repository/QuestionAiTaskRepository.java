package io.github.kamill7779.qforge.persist.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.persist.entity.QuestionAiTask;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionAiTaskRepository extends BaseMapper<QuestionAiTask> {

    default Optional<QuestionAiTask> findByTaskUuid(String taskUuid) {
        return Optional.ofNullable(
                selectOne(Wrappers.<QuestionAiTask>lambdaQuery()
                        .eq(QuestionAiTask::getTaskUuid, taskUuid)));
    }
}
