package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

// Agent调用统计DTO：意图分布、成功率、平均耗时
@Getter
@Setter
public class AgentLogStatsDto {

    // 总调用次数
    private Long totalCalls;

    // 成功次数
    private Long successCalls;

    // 失败次数
    private Long failedCalls;

    // 成功率（0-100）
    private BigDecimal successRate;

    // 平均耗时（毫秒）
    private Long avgLatencyMs;

    // 按意图分组统计
    private List<IntentStat> intentStats = new ArrayList<>();

    @Getter
    @Setter
    public static class IntentStat {

        // 意图名称
        private String intent;

        // 调用次数
        private Long count;

        // 成功次数
        private Long successCount;

        // 失败次数
        private Long failedCount;

        // 成功率
        private BigDecimal successRate;

        // 平均耗时
        private Long avgLatencyMs;
    }
}
