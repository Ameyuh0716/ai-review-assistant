package com.aiservice.aireviewassistant.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// AgentMetrics 单元测试：验证自定义指标能够正确注册并计数
class AgentMetricsTest {

    private MeterRegistry meterRegistry;
    private AgentMetrics agentMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        agentMetrics = new AgentMetrics(meterRegistry);
    }

    @Test
    void shouldRecordAgentRequestDuration() {
        agentMetrics.recordAgentRequest("QUIZ", 120L);

        Timer timer = meterRegistry.find("agent.request.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.getId().getTag("intent")).isEqualTo("QUIZ");
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(120.0);
    }

    @Test
    void shouldIncrementAgentRequestTotal() {
        agentMetrics.incrementAgentRequest("CHAT", true);
        agentMetrics.incrementAgentRequest("CHAT", false);

        Counter successCounter = meterRegistry.find("agent.request.total")
            .tag("intent", "CHAT")
            .tag("status", "success")
            .counter();
        Counter failureCounter = meterRegistry.find("agent.request.total")
            .tag("intent", "CHAT")
            .tag("status", "failure")
            .counter();

        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1);
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1);
    }

    @Test
    void shouldIncrementToolCall() {
        agentMetrics.incrementToolCall("QuizTool");

        Counter counter = meterRegistry.find("agent.tool.calls")
            .tag("tool", "QuizTool")
            .counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void shouldIncrementLlmError() {
        agentMetrics.incrementLlmError();

        Counter counter = meterRegistry.find("agent.llm.errors").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    @Test
    void shouldRecordRagSearchDuration() {
        agentMetrics.recordRagSearch(80L);

        Timer timer = meterRegistry.find("rag.search.duration").timer();
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(80.0);
    }

    @Test
    void shouldRecordRagRetrievedCount() {
        agentMetrics.recordRagRetrieved(5);
        agentMetrics.recordRagRetrieved(3);

        io.micrometer.core.instrument.DistributionSummary summary = meterRegistry.find("rag.retrieved.count").summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(2);
        assertThat(summary.totalAmount()).isEqualTo(8.0);
    }

    @Test
    void shouldUseUnknownTagWhenIntentIsNull() {
        agentMetrics.recordAgentRequest(null, 50L);
        agentMetrics.incrementAgentRequest(null, true);

        Timer timer = meterRegistry.find("agent.request.duration").timer();
        Counter counter = meterRegistry.find("agent.request.total").counter();

        assertThat(timer.getId().getTag("intent")).isEqualTo("UNKNOWN");
        assertThat(counter.getId().getTag("intent")).isEqualTo("UNKNOWN");
    }
}
