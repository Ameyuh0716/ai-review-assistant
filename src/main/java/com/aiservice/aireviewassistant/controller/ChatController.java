package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.service.impl.ReviewAssistantAgent;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// 对话控制器（已升级为Agent模式，兼容旧接口）
@Validated
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ReviewAssistantAgent agent;

    public ChatController(ReviewAssistantAgent agent) {
        this.agent = agent;
    }

    @PostMapping
    public String chat(@RequestBody @NotBlank(message = "消息内容不能为空") String message,
                       @RequestParam(required = false) Integer conversationId) {
        // 使用Agent统一处理（自动识别意图并调用相应工具）
        return agent.chat(message, conversationId);
    }
}
