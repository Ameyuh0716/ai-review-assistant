package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.Message;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 消息表 Mapper 接口。
 * <p>
 * 对应数据库表 {@code message}，实体类型为 {@link Message}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于存储多轮对话中的用户消息、助手回复及系统消息。
 * </p>
 */
public interface MessageMapper extends BaseMapper<Message> {
}
