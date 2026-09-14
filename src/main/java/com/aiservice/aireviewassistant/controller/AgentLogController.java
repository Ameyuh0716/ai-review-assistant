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

// Agent调用日志控制器：查询调用日志和统计
@Tag(name = "Agent 观测日志", description = "查询调用日志、意图识别统计")
@RestController
@RequestMapping("/api/agent-logs")
public class AgentLogController {

    private final AgentLogService agentLogService;

    public AgentLogController(AgentLogService agentLogService) {
        this.agentLogService = agentLogService;
    }

    // 查询所有日志（支持按 conversationId 过滤）
    @Operation(summary = "查询 Agent 调用日志")
    @GetMapping
    public List<AgentLog> list(@RequestParam(required = false) Integer conversationId) {
        QueryWrapper<AgentLog> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (conversationId != null) {
            wrapper.eq("conversation_id", conversationId);
        }
        return agentLogService.list(wrapper);
    }

    // 根据ID查询日志
    @GetMapping("/{id}")
    public AgentLog getById(@PathVariable Integer id) {
        return agentLogService.getById(id);
    }

    // 统计Agent调用数据（支持按时间范围过滤）
    @Operation(summary = "统计意图识别数据")
    @GetMapping("/stats")
    public AgentLogStatsDto stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime) {
        return agentLogService.getStats(startTime, endTime);
    }
}
