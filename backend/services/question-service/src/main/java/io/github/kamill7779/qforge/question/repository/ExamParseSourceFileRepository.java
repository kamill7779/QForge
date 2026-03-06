package io.github.kamill7779.qforge.question.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.kamill7779.qforge.question.entity.ExamParseSourceFile;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamParseSourceFileRepository extends BaseMapper<ExamParseSourceFile> {

    default ExamParseSourceFile save(ExamParseSourceFile entity) {
        if (entity.getId() == null) {
            this.insert(entity);
        } else {
            this.updateById(entity);
        }
        return entity;
    }
}
