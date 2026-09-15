package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.StudyPlan;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 学习计划表 Mapper 接口。
 * <p>
 * 对应数据库表 {@code study_plan}，实体类型为 {@link StudyPlan}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于存储从对话中生成的学习目标课程、可用天数及 Markdown 格式的计划内容。
 * </p>
 */
@Mapper
public interface StudyPlanMapper extends BaseMapper<StudyPlan> {
}
