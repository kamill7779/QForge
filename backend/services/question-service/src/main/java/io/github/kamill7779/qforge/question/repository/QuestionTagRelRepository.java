package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.QuestionTagRel;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionTagRelRepository extends BaseMapper<QuestionTagRel> {

    default long countByQuestionId(Long questionId) {
        Long count = this.selectCount(
                Wrappers.<QuestionTagRel>lambdaQuery()
                        .eq(QuestionTagRel::getQuestionId, questionId)
        );
        return count == null ? 0 : count;
    }

    default List<QuestionTagRel> findByQuestionIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }
        return this.selectList(
                Wrappers.<QuestionTagRel>lambdaQuery()
                        .in(QuestionTagRel::getQuestionId, questionIds)
                        .orderByAsc(QuestionTagRel::getQuestionId, QuestionTagRel::getId)
        );
    }

    default QuestionTagRel save(QuestionTagRel entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}

