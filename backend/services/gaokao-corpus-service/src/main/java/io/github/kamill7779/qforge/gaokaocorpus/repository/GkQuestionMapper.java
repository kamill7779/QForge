package io.github.kamill7779.qforge.gaokaocorpus.repository;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import io.github.kamill7779.qforge.gaokaocorpus.entity.GkQuestion;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GkQuestionMapper extends BaseMapper<GkQuestion> {
}
