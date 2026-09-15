package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.dto.AgentLogStatsDto;
import com.aiservice.aireviewassistant.entity.AgentLog;
import com.aiservice.aireviewassistant.mapper.AgentLogMapper;
import com.aiservice.aireviewassistant.service.AgentLogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent 调用日志服务实现类。
 * <p>
 * 基于 MyBatis-Plus 提供的通用 CRUD 能力，扩展实现调用统计：
 * 1. 支持按时间范围过滤日志；
 * 2. 汇总总调用量、成功/失败量、成功率、平均耗时；
 * 3. 按意图（intent）分组统计，帮助分析各业务场景的稳定性和性能。
 * </p>
 */
@Service
public class AgentLogServiceImpl extends ServiceImpl<AgentLogMapper, AgentLog> implements AgentLogService {

    /**
     * 统计 Agent 调用数据，支持按时间范围过滤。
     *
     * @param startTime 开始时间，可为 null
     * @param endTime   结束时间，可为 null
     * @return 包含整体统计与意图分组统计的 DTO
     */
    @Override
    public AgentLogStatsDto getStats(LocalDateTime startTime, LocalDateTime endTime) {
        // 构造时间范围查询条件
        QueryWrapper<AgentLog> wrapper = new QueryWrapper<>();
        if (startTime != null) {
            wrapper.ge("created_at", startTime);
        }
        if (endTime != null) {
            wrapper.le("created_at", endTime);
        }
        List<AgentLog> logs = list(wrapper);

        // 计算整体调用统计指标
        AgentLogStatsDto stats = new AgentLogStatsDto();
        stats.setTotalCalls((long) logs.size());
        stats.setSuccessCalls(logs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count());
        stats.setFailedCalls(logs.size() - stats.getSuccessCalls());
        stats.setSuccessRate(calculateRate(stats.getSuccessCalls(), stats.getTotalCalls()));
        stats.setAvgLatencyMs(calculateAvgLatency(logs));
        stats.setIntentStats(buildIntentStats(logs));
        return stats;
    }

    /**
     * 按意图（intent）分组统计调用量、成功/失败量、成功率与平均耗时。
     * <p>
     * 对 intent 为 null 的记录归入 "UNKNOWN" 分组；结果按调用量降序排列。
     * </p>
     *
     * @param logs Agent 调用日志列表
     * @return 按意图分组的统计列表
     */
    private List<AgentLogStatsDto.IntentStat> buildIntentStats(List<AgentLog> logs) {
        // 按 intent 分组，缺失 intent 的记录标记为 UNKNOWN
        Map<String, List<AgentLog>> grouped = logs.stream()
            .collect(Collectors.groupingBy(l -> l.getIntent() != null ? l.getIntent() : "UNKNOWN"));

        return grouped.entrySet().stream()
            .map(entry -> {
                String intent = entry.getKey();
                List<AgentLog> intentLogs = entry.getValue();
                long total = intentLogs.size();
                long success = intentLogs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count();

                // 构建单个意图的统计对象
                AgentLogStatsDto.IntentStat stat = new AgentLogStatsDto.IntentStat();
                stat.setIntent(intent);
                stat.setCount(total);
                stat.setSuccessCount(success);
                stat.setFailedCount(total - success);
                stat.setSuccessRate(calculateRate(success, total));
                stat.setAvgLatencyMs(calculateAvgLatency(intentLogs));
                return stat;
            })
            // 按调用量降序排列，突出高频意图
            .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
            .collect(Collectors.toList());
    }

    /**
     * 计算成功率，结果保留两位小数。
     *
     * @param success 成功次数
     * @param total   总次数
     * @return 成功率（百分比形式，如 95.00）
     */
    private BigDecimal calculateRate(long success, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(success * 100.0 / total)
            .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算平均耗时（毫秒）。
     * <p>
     * 对 latency_ms 为 null 的记录按 0 毫秒处理，避免空指针。
     * </p>
     *
     * @param logs Agent 调用日志列表
     * @return 平均耗时；空列表返回 0
     */
    private Long calculateAvgLatency(List<AgentLog> logs) {
        if (logs.isEmpty()) {
            return 0L;
        }
        long sum = logs.stream()
            .mapToLong(l -> l.getLatencyMs() != null ? l.getLatencyMs() : 0L)
            .sum();
        return sum / logs.size();
    }
}
