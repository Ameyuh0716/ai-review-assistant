package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.mapper.ConversationMapper;
import com.aiservice.aireviewassistant.service.ConversationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 会话表服务实现类。
 *
 * <p>基于 MyBatis-Plus {@link ServiceImpl} 实现 {@link ConversationService} 接口，
 * 提供会话实体的持久化与基础查询能力。所有数据访问均通过 {@link ConversationMapper} 完成。</p>
 */
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {
}
