package com.aiservice.aireviewassistant.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AgentMetrics 单元测试。
 *
 * <p>测试目标：验证 {@link AgentMetrics} 能够向 Micrometer 注册正确的
 * Timer、Counter、DistributionSummary 等自定义指标，并按预期记录标签和数值。</p>
 */
class AgentMetricsTest {

    /** 简单的内存 MeterRegistry，用于在测试中收集和查询指标。 */
    private MeterRegistry meterRegistry;

    /** 被测对象：自定义业务指标封装类。 */
    private AgentMetrics agentMetrics;

    /**
     * 每个测试方法前的初始化。
     *
     * <p>创建一个新的 SimpleMeterRegistry 和 AgentMetrics 实例，避免指标在测试间相互影响。</p>
     */
    @BeforeEach
    void setUp() {
        // 创建内存中的指标注册表
        meterRegistry = new SimpleMeterRegistry();
        // 注入注册表构造被测对象
        agentMetrics = new AgentMetrics(meterRegistry);
    }

    /**
     * 验证 Agent 请求持续时间被记录为 Timer。
     *
     * <p>测试场景：以 QUIZ 意图调用 recordAgentRequest，耗时 120 毫秒。</p>
     * <p>断言意图：名称为 agent.request.duration 的 Timer 存在，
     * intent 标签为 QUIZ，计数为 1，总耗时为 120.0 毫秒。</p>
     */
    @Test
    void shouldRecordAgentRequestDuration() {
        // 记录一次 QUIZ 意图、耗时 120ms 的 Agent 请求
        agentMetrics.recordAgentRequest("QUIZ", 120L);

        // 从注册表中查找对应的 Timer
        Timer timer = meterRegistry.find("agent.request.duration").timer();
        // 验证 Timer 已被注册
        assertThat(timer).isNotNull();
        // 验证 intent 标签值符合预期
        assertThat(timer.getId().getTag("intent")).isEqualTo("QUIZ");
        // 验证只记录了一次
        assertThat(timer.count()).isEqualTo(1);
        // 验证累计耗时为 120ms
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(120.0);
    }

    /**
     * 验证 Agent 请求总次数按成功/失败分别计数。
     *
     * <p>测试场景：对 CHAT 意图分别记录一次成功和一次失败。</p>
     * <p>断言意图：成功和失败的 Counter 各存在且计数均为 1。</p>
     */
    @Test
    void shouldIncrementAgentRequestTotal() {
        // 记录一次 CHAT 意图的成功请求
        agentMetrics.incrementAgentRequest("CHAT", true);
        // 记录一次 CHAT 意图的失败请求
        agentMetrics.incrementAgentRequest("CHAT", false);

        // 按 intent=CHAT, status=success 查找 Counter
        Counter successCounter = meterRegistry.find("agent.request.total")
            .tag("intent", "CHAT")
            .tag("status", "success")
            .counter();
        // 按 intent=CHAT, status=failure 查找 Counter
        Counter failureCounter = meterRegistry.find("agent.request.total")
            .tag("intent", "CHAT")
            .tag("status", "failure")
            .counter();

        // 验证成功 Counter 存在且计数为 1
        assertThat(successCounter).isNotNull();
        assertThat(successCounter.count()).isEqualTo(1);
        // 验证失败 Counter 存在且计数为 1
        assertThat(failureCounter).isNotNull();
        assertThat(failureCounter.count()).isEqualTo(1);
    }

    /**
     * 验证工具调用次数被记录为 Counter。
     *
     * <p>测试场景：调用 incrementToolCall 记录 QuizTool 的一次调用。</p>
     * <p>断言意图：名称为 agent.tool.calls、tool 标签为 QuizTool 的 Counter 存在且计数为 1。</p>
     */
    @Test
    void shouldIncrementToolCall() {
        // 记录一次 QuizTool 调用
        agentMetrics.incrementToolCall("QuizTool");

        // 查找 tool=QuizTool 的 Counter
        Counter counter = meterRegistry.find("agent.tool.calls")
            .tag("tool", "QuizTool")
            .counter();
        // 验证 Counter 存在且计数为 1
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    /**
     * 验证 LLM 错误次数计数器递增。
     *
     * <p>测试场景：调用 incrementLlmError 一次。</p>
     * <p>断言意图：名称为 agent.llm.errors 的 Counter 存在且计数为 1。</p>
     */
    @Test
    void shouldIncrementLlmError() {
        // 记录一次 LLM 调用错误
        agentMetrics.incrementLlmError();

        // 查找 LLM 错误 Counter
        Counter counter = meterRegistry.find("agent.llm.errors").counter();
        // 验证 Counter 存在且计数为 1
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1);
    }

    /**
     * 验证 RAG 检索持续时间被记录为 Timer。
     *
     * <p>测试场景：调用 recordRagSearch 记录 80 毫秒的检索耗时。</p>
     * <p>断言意图：名称为 rag.search.duration 的 Timer 存在，计数为 1，总耗时为 80.0 毫秒。</p>
     */
    @Test
    void shouldRecordRagSearchDuration() {
        // 记录一次耗时 80ms 的 RAG 检索
        agentMetrics.recordRagSearch(80L);

        // 查找 RAG 检索耗时 Timer
        Timer timer = meterRegistry.find("rag.search.duration").timer();
        // 验证 Timer 存在且计数与累计耗时正确
        assertThat(timer).isNotNull();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(80.0);
    }

    /**
     * 验证 RAG 检索返回文档数量被记录为 DistributionSummary。
     *
     * <p>测试场景：分别记录 5 条和 3 条检索结果。</p>
     * <p>断言意图：名称为 rag.retrieved.count 的 Summary 存在，记录次数为 2，总值为 8.0。</p>
     */
    @Test
    void shouldRecordRagRetrievedCount() {
        // 记录两次 RAG 检索返回的文档数量
        agentMetrics.recordRagRetrieved(5);
        agentMetrics.recordRagRetrieved(3);

        // 查找 RAG 检索结果数量分布摘要
        io.micrometer.core.instrument.DistributionSummary summary = meterRegistry.find("rag.retrieved.count").summary();
        // 验证 Summary 存在
        assertThat(summary).isNotNull();
        // 验证共记录 2 次
        assertThat(summary.count()).isEqualTo(2);
        // 验证累计值为 5 + 3 = 8
        assertThat(summary.totalAmount()).isEqualTo(8.0);
    }

    /**
     * 验证意图为空时使用 UNKNOWN 标签兜底。
     *
     * <p>测试场景：分别对 recordAgentRequest 和 incrementAgentRequest 传入 null 意图。</p>
     * <p>断言意图：记录后的 Timer 和 Counter 的 intent 标签均为 UNKNOWN。</p>
     */
    @Test
    void shouldUseUnknownTagWhenIntentIsNull() {
        // 使用 null 意图记录请求耗时
        agentMetrics.recordAgentRequest(null, 50L);
        // 使用 null 意图记录请求总次数
        agentMetrics.incrementAgentRequest(null, true);

        // 查找请求耗时 Timer 和请求总数 Counter
        Timer timer = meterRegistry.find("agent.request.duration").timer();
        Counter counter = meterRegistry.find("agent.request.total").counter();

        // 验证两者均使用 UNKNOWN 作为 intent 标签值
        assertThat(timer.getId().getTag("intent")).isEqualTo("UNKNOWN");
        assertThat(counter.getId().getTag("intent")).isEqualTo("UNKNOWN");
    }
}
