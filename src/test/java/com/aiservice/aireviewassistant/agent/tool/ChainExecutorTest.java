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

// ChainExecutor 单元测试：验证链式请求解析与主题继承
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
        chainExecutor = new ChainExecutor(toolRegistry);
        lenient().when(toolRegistry.getTool("SUMMARY")).thenReturn(summaryTool);
        lenient().when(toolRegistry.getTool("QUIZ")).thenReturn(quizTool);
        lenient().when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        lenient().when(summaryTool.getName()).thenReturn("SUMMARY");
        lenient().when(quizTool.getName()).thenReturn("QUIZ");
        lenient().when(chatTool.getName()).thenReturn("CHAT");
        lenient().when(summaryTool.validate(any())).thenReturn(true);
        lenient().when(quizTool.validate(any())).thenReturn(true);
        lenient().when(chatTool.validate(any())).thenReturn(true);
    }

    @Test
    void shouldDetectChainRequest() {
        assertThat(chainExecutor.isChainRequest("先总结第三章，再出3道题")).isTrue();
        assertThat(chainExecutor.isChainRequest("总结第三章")).isFalse();
    }

    @Test
    void shouldParseSummaryThenQuizChain() {
        List<ChainExecutor.ChainStep> steps = chainExecutor.parseChain("先总结第三章，再出3道题");

        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).intent()).isEqualTo("SUMMARY");
        assertThat(steps.get(0).parameters().get("topic")).isEqualTo("第三章");
        assertThat(steps.get(1).intent()).isEqualTo("QUIZ");
        assertThat(steps.get(1).parameters().get("count")).isEqualTo("3");
    }

    @Test
    void shouldInheritTopicAcrossSteps() {
        when(summaryTool.execute(any())).thenReturn("第三章总结内容");
        when(quizTool.execute(any())).thenReturn("第三章题目");

        String result = chainExecutor.executeChain("先总结第三章，再出2道题", 1, null);

        assertThat(result).contains("第三章总结内容");
        assertThat(result).contains("第三章题目");
    }

    @Test
    void shouldReturnEmptyResultForUnparsableChain() {
        String result = chainExecutor.executeChain("先再然后", 1, null);

        assertThat(result).contains("未能识别");
    }
}
