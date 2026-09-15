package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.dto.AgentLogStatsDto;
import com.aiservice.aireviewassistant.entity.AgentLog;
import com.aiservice.aireviewassistant.service.AgentLogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 调用日志控制器。
 * <p>提供 Agent 调用日志查询与意图识别统计接口，基础路径为 /api/agent-logs。</p>
 */
@Tag(name = "Agent 观测日志", description = "查询调用日志、意图识别统计")
@RestController
@RequestMapping("/api/agent-logs")
public class AgentLogController {

    private final AgentLogService agentLogService;

    /**
     * 通过依赖注入构造控制器。
     *
     * @param agentLogService Agent 日志服务
     */
    public AgentLogController(AgentLogService agentLogService) {
        this.agentLogService = agentLogService;
    }

    /**
     * 查询 Agent 调用日志列表。
     * <p>GET /api/agent-logs，支持按 conversationId 过滤，结果按创建时间倒序排列。</p>
     *
     * @param conversationId 会话 ID（可选）
     * @return Agent 调用日志列表
     */
    @Operation(summary = "查询 Agent 调用日志")
    @GetMapping
    public List<AgentLog> list(@RequestParam(required = false) Integer conversationId) {
        QueryWrapper<AgentLog> wrapper = new QueryWrapper<>();
        // 按创建时间倒序排列，最新日志在前
        wrapper.orderByDesc("created_at");
        // 如果指定了会话 ID，则按会话过滤
        if (conversationId != null) {
            wrapper.eq("conversation_id", conversationId);
        }
        return agentLogService.list(wrapper);
    }

    /**
     * 根据 ID 查询 Agent 调用日志。
     * <p>GET /api/agent-logs/{id}</p>
     *
     * @param id 日志主键
     * @return Agent 调用日志实体
     */
    @GetMapping("/{id}")
    public AgentLog getById(@PathVariable Integer id) {
        return agentLogService.getById(id);
    }

    /**
     * 统计 Agent 调用数据。
     * <p>GET /api/agent-logs/stats，支持按时间范围过滤。</p>
     *
     * @param startTime 开始时间（ISO 日期时间格式，可选）
     * @param endTime 结束时间（ISO 日期时间格式，可选）
     * @return Agent 调用统计数据
     */
    @Operation(summary = "统计意图识别数据")
    @GetMapping("/stats")
    public AgentLogStatsDto stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return agentLogService.getStats(startTime, endTime);
    }
}
