package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.annotation.RateLimit;
import com.aiservice.aireviewassistant.common.SseUtils;
import com.aiservice.aireviewassistant.service.impl.ReviewAssistantAgent;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

/**
 * Agent 统一对话控制器。
 * <p>作为系统的统一智能入口，整合出题、复习计划、问答等多种能力，支持多轮会话。
 * 提供普通非流式对话与 SSE 流式对话两种调用方式。</p>
 */
@Tag(name = "Agent 对话", description = "统一入口：自动识别意图并调用出题/计划/问答等工具")
@Validated
@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ReviewAssistantAgent agent;

    /**
     * 构造方法，注入统一的智能代理服务。
     *
     * @param agent 智能对话代理，负责意图识别与工具调度
     */
    public AgentController(ReviewAssistantAgent agent) {
        this.agent = agent;
    }

    /**
     * 普通非流式对话。
     * <p>HTTP: {@code POST /api/agent/chat?conversationId=1}</p>
     *
     * @param message        用户发送的消息内容，不能为空
     * @param conversationId 可选的会话 ID，用于多轮上下文关联；为空时创建新会话
     * @param currentUserId  可选的当前登录用户 ID，用于用户级数据隔离
     * @return Agent 生成的完整文本回复
     */
    @Operation(summary = "普通对话", description = "非流式对话，自动识别意图并返回完整回复")
    @RateLimit(capacity = 30, duration = 1, unit = java.util.concurrent.TimeUnit.MINUTES, message = "对话请求过于频繁，请稍后再试。")
    @PostMapping("/chat")
    public String chat(@RequestBody @NotBlank(message = "消息内容不能为空") String message,
                       @RequestParam(required = false) Integer conversationId,
                       @RequestParam(required = false) Integer courseId,
                       @RequestAttribute(required = false) Integer currentUserId) {
        return agent.chat(message, conversationId, currentUserId, courseId);
    }

    /**
     * 流式对话（Server-Sent Events）。
     * <p>HTTP: {@code GET /api/agent/stream?message=xxx&conversationId=1}，
     * 返回类型为 {@code text/event-stream}，适用于前端逐字展示 AI 回复。</p>
     *
     * @param message          用户发送的消息内容，不能为空
     * @param conversationId   可选的会话 ID，用于多轮上下文关联
     * @param reuseUserMessage 为 true 时不重复保存用户消息（内容已存在或已就地更新）
     * @param courseId         可选的当前课程 ID，用于绑定会话课程以支撑学习统计
     * @param currentUserId    可选的当前登录用户 ID
     * @return SSE 流式文本序列
     */
    @Operation(summary = "流式对话", description = "SSE 流式返回，支持停止和重新生成")
    @RateLimit(capacity = 30, duration = 1, unit = java.util.concurrent.TimeUnit.MINUTES, message = "流式对话请求过于频繁，请稍后再试。")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam("message") @NotBlank(message = "消息内容不能为空") String message,
                               @RequestParam(required = false) Integer conversationId,
                               @RequestParam(required = false) Boolean reuseUserMessage,
                               @RequestParam(required = false) Integer courseId,
                               @RequestParam(required = false) Integer assistantMessageId,
                               @RequestParam(required = false) Integer historyBeforeId,
                               @RequestAttribute(required = false) Integer currentUserId) {
        return agent.chatStream(message, conversationId, currentUserId, reuseUserMessage, courseId,
                        assistantMessageId, historyBeforeId)
                // ⚠️ 关键: SSE 规范要求客户端剥离每个 data 行后的一个前导空格
                // 详见 {@link SseUtils#padSseLines(String)}
                .map(SseUtils::padSseLines);
    }
}
