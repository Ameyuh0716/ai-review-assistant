package com.aiservice.aireviewassistant.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

// Agent 与 RAG 链路自定义指标：用于 Micrometer / Prometheus 监控
@Component
public class AgentMetrics {

    private final MeterRegistry meterRegistry;

    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // 记录一次 Agent 请求耗时（毫秒）
    public void recordAgentRequest(String intent, long durationMs) {
        Timer.builder("agent.request.duration")
            .tag("intent", intent != null ? intent : "UNKNOWN")
            .description("Agent 请求处理耗时")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // 记录 Agent 请求总数（按意图和成功与否分类）
    public void incrementAgentRequest(String intent, boolean success) {
        Counter.builder("agent.request.total")
            .tag("intent", intent != null ? intent : "UNKNOWN")
            .tag("status", success ? "success" : "failure")
            .description("Agent 请求总数")
            .register(meterRegistry)
            .increment();
    }

    // 记录工具调用次数
    public void incrementToolCall(String toolName) {
        Counter.builder("agent.tool.calls")
            .tag("tool", toolName != null ? toolName : "UNKNOWN")
            .description("Agent 工具调用次数")
            .register(meterRegistry)
            .increment();
    }

    // 记录 LLM 调用失败次数
    public void incrementLlmError() {
        Counter.builder("agent.llm.errors")
            .description("LLM 调用失败次数")
            .register(meterRegistry)
            .increment();
    }

    // 记录 RAG 检索耗时（毫秒）
    public void recordRagSearch(long durationMs) {
        Timer.builder("rag.search.duration")
            .description("RAG 向量检索耗时")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    // 记录 RAG 召回文档数量
    public void recordRagRetrieved(int count) {
        DistributionSummary.builder("rag.retrieved.count")
            .description("RAG 召回文档数量")
            .register(meterRegistry)
            .record(count);
    }
}
