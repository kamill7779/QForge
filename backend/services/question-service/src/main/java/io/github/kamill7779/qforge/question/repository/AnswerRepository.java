package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.Answer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AnswerRepository extends BaseMapper<Answer> {

    default long countByQuestionId(Long questionId) {
        Long count = this.selectCount(
                Wrappers.<Answer>lambdaQuery()
                        .eq(Answer::getQuestionId, questionId)
        );
        return count == null ? 0 : count;
    }

    default Answer save(Answer entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }

    default Map<Long, Long> countByQuestionIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return Map.of();
        }

        List<Map<String, Object>> rows = this.selectMaps(
                Wrappers.<Answer>query()
                        .select("question_id AS questionId", "COUNT(*) AS answerCount")
                        .in("question_id", questionIds)
                        .groupBy("question_id")
        );
        Map<Long, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object questionIdRaw = row.get("questionId");
            Object answerCountRaw = row.get("answerCount");
            if (!(questionIdRaw instanceof Number) || !(answerCountRaw instanceof Number)) {
                continue;
            }
            result.put(((Number) questionIdRaw).longValue(), ((Number) answerCountRaw).longValue());
        }
        return result;
    }

    default List<Answer> findByQuestionIds(List<Long> questionIds) {
        if (questionIds == null || questionIds.isEmpty()) {
            return List.of();
        }

        return this.selectList(
                Wrappers.<Answer>lambdaQuery()
                        .in(Answer::getQuestionId, questionIds)
                        .orderByAsc(Answer::getQuestionId, Answer::getSortOrder)
        );
    }
}
