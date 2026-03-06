package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.ExamParseQuestion;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamParseQuestionRepository extends BaseMapper<ExamParseQuestion> {

    default List<ExamParseQuestion> findByTaskUuidOrderBySeqNo(String taskUuid) {
        return this.selectList(
                Wrappers.<ExamParseQuestion>lambdaQuery()
                        .eq(ExamParseQuestion::getTaskUuid, taskUuid)
                        .orderByAsc(ExamParseQuestion::getSeqNo)
        );
    }

    default Optional<ExamParseQuestion> findByTaskUuidAndSeqNo(String taskUuid, int seqNo) {
        ExamParseQuestion q = this.selectOne(
                Wrappers.<ExamParseQuestion>lambdaQuery()
                        .eq(ExamParseQuestion::getTaskUuid, taskUuid)
                        .eq(ExamParseQuestion::getSeqNo, seqNo)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(q);
    }

    default List<ExamParseQuestion> findPendingByTaskUuid(String taskUuid) {
        return this.selectList(
                Wrappers.<ExamParseQuestion>lambdaQuery()
                        .eq(ExamParseQuestion::getTaskUuid, taskUuid)
                        .eq(ExamParseQuestion::getConfirmStatus, "PENDING")
                        .orderByAsc(ExamParseQuestion::getSeqNo)
        );
    }

    default ExamParseQuestion save(ExamParseQuestion entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
