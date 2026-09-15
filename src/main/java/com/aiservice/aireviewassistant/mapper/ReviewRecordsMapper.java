package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.ReviewRecords;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 复习记录表 Mapper 接口。
 * <p>
 * 对应数据库表 {@code review_records}，实体类型为 {@link ReviewRecords}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于存储用户在学习过程中的提问与 AI 回答，便于后续追踪复习情况。
 * </p>
 */
public interface ReviewRecordsMapper extends BaseMapper<ReviewRecords> {
}
