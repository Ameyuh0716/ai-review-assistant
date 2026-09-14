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

// Agent调用日志 服务实现类
@Service
public class AgentLogServiceImpl extends ServiceImpl<AgentLogMapper, AgentLog> implements AgentLogService {

    @Override
    public AgentLogStatsDto getStats(LocalDateTime startTime, LocalDateTime endTime) {
        QueryWrapper<AgentLog> wrapper = new QueryWrapper<>();
        if (startTime != null) {
            wrapper.ge("created_at", startTime);
        }
        if (endTime != null) {
            wrapper.le("created_at", endTime);
        }
        List<AgentLog> logs = list(wrapper);

        AgentLogStatsDto stats = new AgentLogStatsDto();
        stats.setTotalCalls((long) logs.size());
        stats.setSuccessCalls(logs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count());
        stats.setFailedCalls(logs.size() - stats.getSuccessCalls());
        stats.setSuccessRate(calculateRate(stats.getSuccessCalls(), stats.getTotalCalls()));
        stats.setAvgLatencyMs(calculateAvgLatency(logs));
        stats.setIntentStats(buildIntentStats(logs));
        return stats;
    }

    // 按意图分组统计
    private List<AgentLogStatsDto.IntentStat> buildIntentStats(List<AgentLog> logs) {
        Map<String, List<AgentLog>> grouped = logs.stream()
            .collect(Collectors.groupingBy(l -> l.getIntent() != null ? l.getIntent() : "UNKNOWN"));

        return grouped.entrySet().stream()
            .map(entry -> {
                String intent = entry.getKey();
                List<AgentLog> intentLogs = entry.getValue();
                long total = intentLogs.size();
                long success = intentLogs.stream().filter(l -> Boolean.TRUE.equals(l.getSuccess())).count();

                AgentLogStatsDto.IntentStat stat = new AgentLogStatsDto.IntentStat();
                stat.setIntent(intent);
                stat.setCount(total);
                stat.setSuccessCount(success);
                stat.setFailedCount(total - success);
                stat.setSuccessRate(calculateRate(success, total));
                stat.setAvgLatencyMs(calculateAvgLatency(intentLogs));
                return stat;
            })
            .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
            .collect(Collectors.toList());
    }

    // 计算成功率
    private BigDecimal calculateRate(long success, long total) {
        if (total == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(success * 100.0 / total)
            .setScale(2, RoundingMode.HALF_UP);
    }

    // 计算平均耗时
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
