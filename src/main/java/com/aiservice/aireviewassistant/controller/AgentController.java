package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.annotation.RateLimit;
import com.aiservice.aireviewassistant.service.impl.ReviewAssistantAgent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

// Agent统一对话控制器 - 整合所有功能，支持多轮会话
@Tag(name = "Agent 对话", description = "统一入口：自动识别意图并调用出题/计划/问答等工具")
@Validated
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ReviewAssistantAgent agent;

    public AgentController(ReviewAssistantAgent agent) {
        this.agent = agent;
    }

    // 普通对话：POST /api/agent/chat?conversationId=1
    @Operation(summary = "普通对话", description = "非流式对话，自动识别意图并返回完整回复")
    @RateLimit(capacity = 30, duration = 1, unit = java.util.concurrent.TimeUnit.MINUTES, message = "对话请求过于频繁，请稍后再试。")
    @PostMapping("/chat")
    public String chat(@RequestBody @NotBlank(message = "消息内容不能为空") String message,
                       @RequestParam(required = false) Integer conversationId,
                       @RequestAttribute(required = false) Integer currentUserId) {
        return agent.chat(message, conversationId, currentUserId);
    }

    // 流式对话（SSE）：GET /api/agent/stream?message=xxx&conversationId=1
    @Operation(summary = "流式对话", description = "SSE 流式返回，支持停止和重新生成")
    @RateLimit(capacity = 30, duration = 1, unit = java.util.concurrent.TimeUnit.MINUTES, message = "流式对话请求过于频繁，请稍后再试。")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam("message") @NotBlank(message = "消息内容不能为空") String message,
                               @RequestParam(required = false) Integer conversationId,
                               @RequestAttribute(required = false) Integer currentUserId) {
        return agent.chatStream(message, conversationId, currentUserId);
    }
}
