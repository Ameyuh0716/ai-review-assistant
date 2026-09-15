package com.aiservice.aireviewassistant.agent.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ChainExecutor 单元测试。
 * <p>
 * 测试目标：验证链式请求的识别、多步骤解析、步骤间主题继承，以及无法解析时的降级处理。
 */
@ExtendWith(MockitoExtension.class)
class ChainExecutorTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private AgentTool summaryTool;

    @Mock
    private AgentTool quizTool;

    @Mock
    private AgentTool chatTool;

    private ChainExecutor chainExecutor;

    @BeforeEach
    void setUp() {
        // 初始化被测对象
        chainExecutor = new ChainExecutor(toolRegistry);

        // 注册常用的 mock 工具，避免每个测试重复配置；使用 lenient 防止未引用时报错
        lenient().when(toolRegistry.getTool("SUMMARY")).thenReturn(summaryTool);
        lenient().when(toolRegistry.getTool("QUIZ")).thenReturn(quizTool);
        lenient().when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);

        // 配置 mock 工具的名称与校验行为
        lenient().when(summaryTool.getName()).thenReturn("SUMMARY");
        lenient().when(quizTool.getName()).thenReturn("QUIZ");
        lenient().when(chatTool.getName()).thenReturn("CHAT");
        lenient().when(summaryTool.validate(any())).thenReturn(true);
        lenient().when(quizTool.validate(any())).thenReturn(true);
        lenient().when(chatTool.validate(any())).thenReturn(true);
    }

    /**
     * 测试场景：判断用户输入是否包含多个连续意图。
     * <p>
     * 准备条件：提供包含“先…再…”的复合句与单一意图的简单句。
     * 断言意图：复合句应被识别为链式请求，简单句不应被识别。
     */
    @Test
    void shouldDetectChainRequest() {
        assertThat(chainExecutor.isChainRequest("先总结第三章，再出3道题")).isTrue();
        assertThat(chainExecutor.isChainRequest("总结第三章")).isFalse();
    }

    /**
     * 测试场景：解析“总结 + 出题”的链式请求。
     * <p>
     * 准备条件：输入包含明确主题与题目数量。
     * 断言意图：应解析出两个步骤，第一步为 SUMMARY 并提取 topic，第二步为 QUIZ 并提取 count。
     */
    @Test
    void shouldParseSummaryThenQuizChain() {
        List<ChainExecutor.ChainStep> steps = chainExecutor.parseChain("先总结第三章，再出3道题");

        // 验证步骤数量与每一步的意图、参数
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).intent()).isEqualTo("SUMMARY");
        assertThat(steps.get(0).parameters().get("topic")).isEqualTo("第三章");
        assertThat(steps.get(1).intent()).isEqualTo("QUIZ");
        assertThat(steps.get(1).parameters().get("count")).isEqualTo("3");
    }

    /**
     * 测试场景：链式执行时，后续步骤应继承前面步骤提取的主题。
     * <p>
     * 准备条件：mock SUMMARY 与 QUIZ 工具返回包含主题的结果。
     * 断言意图：最终返回结果中应同时包含两个步骤的输出内容。
     */
    @Test
    void shouldInheritTopicAcrossSteps() {
        // 模拟工具执行结果
        when(summaryTool.execute(any())).thenReturn("第三章总结内容");
        when(quizTool.execute(any())).thenReturn("第三章题目");

        String result = chainExecutor.executeChain("先总结第三章，再出2道题", 1, null);

        // 验证链式执行结果包含各步骤输出
        assertThat(result).contains("第三章总结内容");
        assertThat(result).contains("第三章题目");
    }

    /**
     * 测试场景：输入无法解析为有效链式步骤时的降级处理。
     * <p>
     * 准备条件：提供不含明确意图与参数的模糊输入。
     * 断言意图：执行结果中应包含“未能识别”提示信息。
     */
    @Test
    void shouldReturnEmptyResultForUnparsableChain() {
        String result = chainExecutor.executeChain("先再然后", 1, null);

        assertThat(result).contains("未能识别");
    }
}
