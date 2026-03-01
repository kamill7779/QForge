package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.Question;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuestionRepository extends BaseMapper<Question> {

    default Optional<Question> findByQuestionUuidAndOwnerUser(String questionUuid, String ownerUser) {
        Question question = this.selectOne(
                Wrappers.<Question>lambdaQuery()
                        .eq(Question::getQuestionUuid, questionUuid)
                        .eq(Question::getOwnerUser, ownerUser)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(question);
    }

    default List<Question> findAllByOwnerUser(String ownerUser) {
        return this.selectList(
                Wrappers.<Question>lambdaQuery()
                        .eq(Question::getOwnerUser, ownerUser)
                        .orderByDesc(Question::getUpdatedAt)
        );
    }

    default Question save(Question entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
