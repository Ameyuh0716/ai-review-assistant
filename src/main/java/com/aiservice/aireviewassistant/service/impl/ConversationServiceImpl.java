package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.mapper.ConversationMapper;
import com.aiservice.aireviewassistant.service.ConversationService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

// 会话表 服务实现类
@Service
public class ConversationServiceImpl extends ServiceImpl<ConversationMapper, Conversation> implements ConversationService {
}
