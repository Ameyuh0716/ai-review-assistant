package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.Courses;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 课程表 Mapper 接口。
 * <p>
 * 对应数据库表 {@code courses}，实体类型为 {@link Courses}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于存储用户创建的课程名称、描述及所属用户等基本信息。
 * </p>
 */
public interface CoursesMapper extends BaseMapper<Courses> {
}
