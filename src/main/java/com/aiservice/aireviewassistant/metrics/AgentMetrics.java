package com.aiservice.aireviewassistant.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Agent 与 RAG 链路自定义指标组件。
 * <p>
 * 基于 Micrometer 注册 Counter / Timer / DistributionSummary，
 * 用于 Prometheus 等监控系统采集 Agent 请求、工具调用、LLM 错误及 RAG 检索相关指标。
 * </p>
 */
@Component
public class AgentMetrics {

    private final MeterRegistry meterRegistry;

    /**
     * 构造方法。
     *
     * @param meterRegistry Micrometer 注册表，由 Spring 自动注入
     */
    public AgentMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录一次 Agent 请求的处理耗时。
     *
     * @param intent     用户请求意图，为空时使用 {@code UNKNOWN}
     * @param durationMs 处理耗时，单位毫秒
     */
    public void recordAgentRequest(String intent, long durationMs) {
        Timer.builder("agent.request.duration")
            .tag("intent", intent != null ? intent : "UNKNOWN")
            .description("Agent 请求处理耗时")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 Agent 请求总数。
     * <p>
     * 按请求意图与成功/失败状态打标签，便于统计不同意图的调用量与成功率。
     * </p>
     *
     * @param intent  用户请求意图，为空时使用 {@code UNKNOWN}
     * @param success 是否成功
     */
    public void incrementAgentRequest(String intent, boolean success) {
        Counter.builder("agent.request.total")
            .tag("intent", intent != null ? intent : "UNKNOWN")
            .tag("status", success ? "success" : "failure")
            .description("Agent 请求总数")
            .register(meterRegistry)
            .increment();
    }

    /**
     * 记录 Agent 工具调用次数。
     *
     * @param toolName 工具名称，为空时使用 {@code UNKNOWN}
     */
    public void incrementToolCall(String toolName) {
        Counter.builder("agent.tool.calls")
            .tag("tool", toolName != null ? toolName : "UNKNOWN")
            .description("Agent 工具调用次数")
            .register(meterRegistry)
            .increment();
    }

    /**
     * 记录 LLM 调用失败次数。
     */
    public void incrementLlmError() {
        Counter.builder("agent.llm.errors")
            .description("LLM 调用失败次数")
            .register(meterRegistry)
            .increment();
    }

    /**
     * 记录一次 RAG 向量检索的耗时。
     *
     * @param durationMs 检索耗时，单位毫秒
     */
    public void recordRagSearch(long durationMs) {
        Timer.builder("rag.search.duration")
            .description("RAG 向量检索耗时")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 RAG 召回文档数量。
     *
     * @param count 召回文档数量
     */
    public void recordRagRetrieved(int count) {
        DistributionSummary.builder("rag.retrieved.count")
            .description("RAG 召回文档数量")
            .register(meterRegistry)
            .record(count);
    }
}
