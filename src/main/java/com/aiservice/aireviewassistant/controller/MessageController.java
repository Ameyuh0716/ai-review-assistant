package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.entity.Message;
import com.aiservice.aireviewassistant.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 消息控制器：查询会话中的历史消息
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    // 根据会话ID查询消息历史
    @GetMapping
    public List<Message> listByConversation(@RequestParam Integer conversationId) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId);
        wrapper.orderByAsc("created_at");
        return messageService.list(wrapper);
    }

    // 新增消息（通常由 Agent 内部调用，也可外部手动补充）
    @PostMapping
    public boolean save(@RequestBody Message message) {
        return messageService.save(message);
    }
}
