package io.github.kamill7779.qforge.ocr.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.ocr.entity.AiAnalysisTask;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AiAnalysisTaskRepository extends BaseMapper<AiAnalysisTask> {

    default Optional<AiAnalysisTask> findByTaskUuid(String taskUuid) {
        AiAnalysisTask task = this.selectOne(
                Wrappers.<AiAnalysisTask>lambdaQuery()
                        .eq(AiAnalysisTask::getTaskUuid, taskUuid)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(task);
    }
}
