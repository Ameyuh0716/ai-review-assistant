package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.Conversation;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 会话表 Mapper 接口。
 * <p>
 * 对应数据库表 {@code conversation}，实体类型为 {@link Conversation}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于存储多轮对话的会话基本信息，一条会话可包含多条消息记录。
 * </p>
 */
public interface ConversationMapper extends BaseMapper<Conversation> {
}
