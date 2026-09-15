package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.Conversation;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 会话表服务接口。
 *
 * <p>负责 {@link Conversation} 实体的基础 CRUD 操作，由 MyBatis-Plus {@link IService} 提供默认实现。
 * 业务上用于管理用户与 AI 之间的聊天会话元数据，不包含会话内消息的存储逻辑。</p>
 */
public interface ConversationService extends IService<Conversation> {
}
