package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ExplainTool 单元测试。
 * <p>
 * 测试目标：验证解释工具在参数给定、从消息中提取概念、流式输出以及参数校验等场景下的行为。
 */
@ExtendWith(MockitoExtension.class)
class ExplainToolTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private RagService ragService;

    @Mock
    private PromptTemplate promptTemplate;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    private ExplainTool explainTool;

    @BeforeEach
    void setUp() {
        explainTool = new ExplainTool(chatClient, ragService, promptTemplate);
    }

    /**
     * 测试场景：参数中已提供 concept 时直接解释该概念。
     * <p>
     * 准备条件：ToolContext 参数包含 concept=第三范式，mock RAG 检索与 ChatClient 调用链。
     * 断言意图：应调用 RAG 检索该概念上下文，并返回 ChatClient 生成的解释内容。
     */
    @Test
    void shouldExplainConceptParameter() {
        ToolContext context = new ToolContext("随便", 1, Map.of("concept", "第三范式"), null);

        // mock RAG 检索与 prompt 渲染
        when(ragService.retrieveContext("第三范式", 1)).thenReturn("范式相关内容");
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");

        // mock ChatClient 调用链
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("第三范式解释");

        String result = explainTool.execute(context);

        // 验证返回结果与 RAG 检索调用
        assertThat(result).isEqualTo("第三范式解释");
        verify(ragService).retrieveContext("第三范式", 1);
    }

    /**
     * 测试场景：参数未提供 concept 时，从用户消息中提取概念。
     * <p>
     * 准备条件：ToolContext 消息为“解释一下数据库索引”，参数为空。
     * 断言意图：应能从消息中识别概念，并返回对应解释。
     */
    @Test
    void shouldExtractConceptFromMessage() {
        ToolContext context = new ToolContext("解释一下数据库索引", 2, null, null);

        // mock RAG 检索、prompt 渲染与 ChatClient 调用链
        when(ragService.retrieveContext(anyString(), any())).thenReturn("索引相关内容");
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("索引解释");

        String result = explainTool.execute(context);

        assertThat(result).isEqualTo("索引解释");
    }

    /**
     * 测试场景：流式输出解释内容。
     * <p>
     * 准备条件：ToolContext 参数包含 concept=B+树，mock stream 调用返回单条数据。
     * 断言意图：stream 方法应返回按顺序发射的 Flux，并在结束时正常完成。
     */
    @Test
    void shouldReturnStreamFromExecute() {
        ToolContext context = new ToolContext("解释 B+树", 3, Map.of("concept", "B+树"), null);

        // mock RAG 检索、prompt 渲染与 ChatClient stream 调用链
        when(ragService.retrieveContext(anyString(), any())).thenReturn("上下文");
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("B+树解释"));

        // 验证流按预期发射并正常完成
        StepVerifier.create(explainTool.stream(context))
            .expectNext("B+树解释")
            .verifyComplete();
    }

    /**
     * 测试场景：缺少有效概念时应拒绝执行。
     * <p>
     * 准备条件：ToolContext 消息为“解释一下”，参数中无 concept。
     * 断言意图：validate 应返回 false，且错误提示包含“概念或题目”。
     */
    @Test
    void shouldRejectEmptyConcept() {
        ToolContext context = new ToolContext("解释一下", 4, null, null);

        // 验证校验不通过且错误提示符合预期
        assertThat(explainTool.validate(context)).isFalse();
        assertThat(explainTool.getValidationError(context)).contains("概念或题目");
    }
}
