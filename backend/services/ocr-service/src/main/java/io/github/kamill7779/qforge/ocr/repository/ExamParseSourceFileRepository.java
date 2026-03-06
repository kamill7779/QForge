package io.github.kamill7779.qforge.ocr.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import io.github.kamill7779.qforge.ocr.entity.ExamParseSourceFile;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ExamParseSourceFileRepository extends BaseMapper<ExamParseSourceFile> {

    default List<ExamParseSourceFile> findByTaskUuidOrderByFileIndex(String taskUuid) {
        return this.selectList(
                Wrappers.<ExamParseSourceFile>lambdaQuery()
                        .eq(ExamParseSourceFile::getTaskUuid, taskUuid)
                        .orderByAsc(ExamParseSourceFile::getFileIndex)
        );
    }
}
