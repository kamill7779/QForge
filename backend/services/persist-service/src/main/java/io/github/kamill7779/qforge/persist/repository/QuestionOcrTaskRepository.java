package io.github.kamill7779.qforge.persist.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.persist.entity.QuestionOcrTask;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionOcrTaskRepository extends BaseMapper<QuestionOcrTask> {

    default Optional<QuestionOcrTask> findByTaskUuid(String taskUuid) {
        return Optional.ofNullable(
                selectOne(Wrappers.<QuestionOcrTask>lambdaQuery()
                        .eq(QuestionOcrTask::getTaskUuid, taskUuid)));
    }

    default QuestionOcrTask save(QuestionOcrTask entity) {
        if (entity.getId() == null) { insert(entity); } else { updateById(entity); }
        return entity;
    }
}
