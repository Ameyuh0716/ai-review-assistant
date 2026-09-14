package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.Message;
import com.aiservice.aireviewassistant.mapper.MessageMapper;
import com.aiservice.aireviewassistant.service.MessageService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

// 消息表 服务实现类
@Service
public class MessageServiceImpl extends ServiceImpl<MessageMapper, Message> implements MessageService {
}
