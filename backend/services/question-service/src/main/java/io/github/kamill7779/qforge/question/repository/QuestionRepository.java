package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.Question;
import java.util.List;
import java.util.Objects;
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

    default List<Question> findPageByOwnerUser(String ownerUser, int offset, int limit) {
        return this.selectList(
                Wrappers.<Question>lambdaQuery()
                        .eq(Question::getOwnerUser, ownerUser)
                        .orderByDesc(Question::getUpdatedAt)
                        .last("LIMIT " + Math.max(offset, 0) + ", " + Math.max(limit, 1))
        );
    }

    default List<Question> findReadyPageByOwnerUser(String ownerUser, int offset, int limit) {
        return this.selectList(
                Wrappers.<Question>lambdaQuery()
                        .eq(Question::getOwnerUser, ownerUser)
                        .eq(Question::getStatus, "READY")
                        .orderByDesc(Question::getUpdatedAt)
                        .last("LIMIT " + Math.max(offset, 0) + ", " + Math.max(limit, 1))
        );
    }

    default long countByOwnerUser(String ownerUser) {
        return this.selectCount(
                Wrappers.<Question>lambdaQuery()
                        .eq(Question::getOwnerUser, ownerUser)
        );
    }

    default long countReadyByOwnerUser(String ownerUser) {
        return this.selectCount(
                Wrappers.<Question>lambdaQuery()
                        .eq(Question::getOwnerUser, ownerUser)
                        .eq(Question::getStatus, "READY")
        );
    }

    default List<Question> findByQuestionUuidsAndOwnerUser(List<String> questionUuids, String ownerUser) {
        if (questionUuids == null || questionUuids.isEmpty()) {
            return List.of();
        }
        return this.selectList(
                Wrappers.<Question>lambdaQuery()
                        .in(Question::getQuestionUuid, questionUuids)
                        .eq(Question::getOwnerUser, ownerUser)
        );
    }

    default List<String> findDistinctSourcesByOwnerUser(String ownerUser) {
        return this.selectObjs(
                        Wrappers.<Question>query()
                                .select("DISTINCT source")
                                .eq("owner_user", ownerUser)
                                .orderByAsc("source")
                ).stream()
                .map(value -> value == null ? "未分类" : value.toString())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
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
