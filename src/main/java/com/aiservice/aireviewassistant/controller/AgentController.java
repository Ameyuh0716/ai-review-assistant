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
                       @RequestAttribute(required = false) Integer currentUserId) {
        return agent.chat(message, conversationId, currentUserId);
    }

    /**
     * 流式对话（Server-Sent Events）。
     * <p>HTTP: {@code GET /api/agent/stream?message=xxx&conversationId=1}，
     * 返回类型为 {@code text/event-stream}，适用于前端逐字展示 AI 回复。</p>
     *
     * @param message        用户发送的消息内容，不能为空
     * @param conversationId 可选的会话 ID，用于多轮上下文关联
     * @param currentUserId  可选的当前登录用户 ID
     * @return SSE 流式文本序列
     */
    @Operation(summary = "流式对话", description = "SSE 流式返回，支持停止和重新生成")
    @RateLimit(capacity = 30, duration = 1, unit = java.util.concurrent.TimeUnit.MINUTES, message = "流式对话请求过于频繁，请稍后再试。")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(@RequestParam("message") @NotBlank(message = "消息内容不能为空") String message,
                               @RequestParam(required = false) Integer conversationId,
                               @RequestAttribute(required = false) Integer currentUserId) {
        return agent.chatStream(message, conversationId, currentUserId)
                // ⚠️ 关键: SSE 规范要求客户端剥离每个 data 行后的一个前导空格
                // (https://html.spec.whatwg.org/multipage/server-sent-events.html#event-stream-interpretation)
                // Spring 编码器把含换行的 token 拆成多个 "data:" 行, 且不追加空格;
                // 若某行内容本身以空格开头(markdown 缩进/硬换行/列表续行), 该空格会被浏览器吃掉。
                // 故为"每一行"预置一个空格, 经浏览器剥离后与原始 token 逐字节一致。
                .map(AgentController::padSseLines);
    }

    /**
     * 为 SSE 数据的每一行预置一个前导空格。
     * <p>浏览器解析 SSE 时会剥离每个 {@code data:} 行后的一个空格，
     * 服务端逐行补位后即可保证含前导空格的文本（markdown 缩进、硬换行等）无损传输。</p>
     *
     * @param frame 原始 SSE 帧内容
     * @return 每行前均带一个空格的帧内容
     */
    private static String padSseLines(String frame) {
        StringBuilder sb = new StringBuilder(frame.length() + 8);
        sb.append(' ');
        for (int i = 0; i < frame.length(); i++) {
            char c = frame.charAt(i);
            sb.append(c);
            if (c == '\n' && i < frame.length() - 1) {
                // 换行后补位(行尾换行由 Spring 生成的行本身无需再补)
                sb.append(' ');
            }
        }
        return sb.toString();
    }
}
