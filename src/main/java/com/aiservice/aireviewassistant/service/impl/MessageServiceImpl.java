package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.Message;
import com.aiservice.aireviewassistant.mapper.MessageMapper;
import com.aiservice.aireviewassistant.service.MessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * 消息表服务实现类。
 *
 * <p>基于 MyBatis-Plus {@link ServiceImpl} 实现 {@link MessageService} 接口，
 * 提供消息实体的持久化与基础查询能力。所有数据访问均通过 {@link MessageMapper} 完成。</p>
 */
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {
}
