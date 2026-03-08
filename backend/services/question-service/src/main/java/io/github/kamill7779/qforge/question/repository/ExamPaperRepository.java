package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.question.entity.ExamPaper;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamPaperRepository extends BaseMapper<ExamPaper> {

    default List<ExamPaper> findByOwnerUser(String ownerUser) {
        return this.selectList(
                Wrappers.<ExamPaper>lambdaQuery()
                        .eq(ExamPaper::getOwnerUser, ownerUser)
                        .orderByDesc(ExamPaper::getUpdatedAt)
        );
    }

    default Optional<ExamPaper> findByPaperUuidAndOwnerUser(String paperUuid, String ownerUser) {
        ExamPaper paper = this.selectOne(
                Wrappers.<ExamPaper>lambdaQuery()
                        .eq(ExamPaper::getPaperUuid, paperUuid)
                        .eq(ExamPaper::getOwnerUser, ownerUser)
                        .last("LIMIT 1")
        );
        return Optional.ofNullable(paper);
    }

    default ExamPaper save(ExamPaper entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
