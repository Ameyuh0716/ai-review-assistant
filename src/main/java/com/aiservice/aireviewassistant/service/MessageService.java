package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.Message;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 消息表服务接口。
 *
 * <p>负责 {@link Message} 实体的基础 CRUD 操作，由 MyBatis-Plus {@link IService} 提供默认实现。
 * 业务上用于存储聊天会话中的用户提问与 AI 回复内容，不包含消息生成或 AI 调用逻辑。</p>
 */
public interface MessageService extends IService<Message> {
}
