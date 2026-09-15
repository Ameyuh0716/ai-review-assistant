package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Agent 调用统计响应 DTO。
 * <p>用于汇总 Agent 在指定时间范围内的调用情况，包括总调用量、成功/失败分布、成功率、
 * 平均延迟以及按意图（intent）维度的分组统计。</p>
 */
@Getter
@Setter
public class AgentLogStatsDto {

    /** 总调用次数。 */
    private Long totalCalls;

    /** 成功调用次数。 */
    private Long successCalls;

    /** 失败调用次数。 */
    private Long failedCalls;

    /** 成功率，取值范围 0-100，保留两位小数。 */
    private BigDecimal successRate;

    /** 平均耗时，单位：毫秒。 */
    private Long avgLatencyMs;

    /** 按意图分组统计列表；默认为空列表，避免返回 null。 */
    private List<IntentStat> intentStats = new ArrayList<>();

    /**
     * 单个意图维度的调用统计内部 DTO。
     */
    @Getter
    @Setter
    public static class IntentStat {

        /** 意图名称，例如 chat、quiz、plan、summary 等。 */
        private String intent;

        /** 该意图的总调用次数。 */
        private Long count;

        /** 该意图的成功调用次数。 */
        private Long successCount;

        /** 该意图的失败调用次数。 */
        private Long failedCount;

        /** 该意图的成功率，取值范围 0-100，保留两位小数。 */
        private BigDecimal successRate;

        /** 该意图的平均耗时，单位：毫秒。 */
        private Long avgLatencyMs;
    }
}
