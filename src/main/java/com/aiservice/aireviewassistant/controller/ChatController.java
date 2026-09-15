package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.service.impl.ReviewAssistantAgent;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 旧版对话控制器。
 * <p>已升级为 Agent 模式以兼容旧版前端接口，实际对话逻辑由 {@link ReviewAssistantAgent} 统一处理，
 * 支持自动识别意图并调用出题、计划、问答等工具。</p>
 */
@Validated
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ReviewAssistantAgent agent;

    /**
     * 构造方法，注入统一的智能代理服务。
     *
     * @param agent 智能对话代理
     */
    public ChatController(ReviewAssistantAgent agent) {
        this.agent = agent;
    }

    /**
     * 接收用户消息并返回 Agent 生成的完整回复。
     * <p>HTTP: {@code POST /chat}</p>
     *
     * @param message        用户发送的消息内容，不能为空
     * @param conversationId 可选的会话 ID，用于多轮上下文关联
     * @return Agent 生成的完整文本回复
     */
    @PostMapping
    public String chat(@RequestBody @NotBlank(message = "消息内容不能为空") String message,
                       @RequestParam(required = false) Integer conversationId) {
        // 使用 Agent 统一处理（自动识别意图并调用相应工具）
        return agent.chat(message, conversationId);
    }
}
