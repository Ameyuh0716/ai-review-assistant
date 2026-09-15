package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.dto.AgentLogStatsDto;
import com.aiservice.aireviewassistant.entity.AgentLog;
import com.baomidou.mybatisplus.extension.service.IService;

import java.time.LocalDateTime;

/**
 * Agent 调用日志服务接口。
 * <p>
 * 负责持久化 Agent 每次调用的元数据（意图、耗时、成功失败等），
 * 并提供按时间范围聚合的调用统计能力。
 * </p>
 */
public interface AgentLogService extends IService<AgentLog> {

    /**
     * 统计 Agent 调用数据。
     * <p>
     * 默认统计全部记录；传入起止时间时，仅统计该时间范围内的记录。
     * 返回结果包含总调用量、成功/失败量、成功率、平均耗时及按意图分组的统计。
     * </p>
     *
     * @param startTime 开始时间，可为 null
     * @param endTime   结束时间，可为 null
     * @return Agent 调用统计 DTO
     */
    AgentLogStatsDto getStats(LocalDateTime startTime, LocalDateTime endTime);
}
