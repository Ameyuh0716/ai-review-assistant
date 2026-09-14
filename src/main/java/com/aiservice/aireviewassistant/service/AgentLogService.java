package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.dto.AgentLogStatsDto;
import com.aiservice.aireviewassistant.entity.AgentLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

// Agent调用日志 服务接口
public interface AgentLogService extends IService<AgentLog> {

    // 统计Agent调用数据（默认统计全部，支持按时间范围过滤）
    AgentLogStatsDto getStats(LocalDateTime startTime, LocalDateTime endTime);
}
