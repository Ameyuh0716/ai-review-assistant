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
 * {@link SummaryTool} 的单元测试。
 * 验证总结工具能否从显式参数或用户消息中提取主题，
 * 调用 {@link RagService} 检索上下文，并通过 {@link ChatClient} 与 {@link PromptTemplate}
 * 完成同步总结与流式总结。
 */
@ExtendWith(MockitoExtension.class)
class SummaryToolTest {

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

    private SummaryTool summaryTool;

    @BeforeEach
    void setUp() {
        // 初始化被测对象，注入模拟的 ChatClient、RagService 与 PromptTemplate
        summaryTool = new SummaryTool(chatClient, ragService, promptTemplate);
    }

    /**
     * 测试场景：显式参数中提供总结主题。
     * 准备条件：ToolContext 的 parameters 中设置 topic 为“数据库索引”，并模拟 RAG 上下文、
     *          提示词模板与 ChatClient 完整调用链返回固定总结。
     * 断言意图：execute 返回 ChatClient 生成的总结结果，并验证 RAG 检索使用了显式主题。
     */
    @Test
    void shouldGenerateSummaryWithTopicParameter() {
        // 构造工具上下文：parameters 中显式指定 topic 为“数据库索引”，课程 ID 为 1
        ToolContext context = new ToolContext("随便", 1, Map.of("topic", "数据库索引"), null);
        // 模拟 RagService 根据显式主题检索上下文
        when(ragService.retrieveContext("数据库索引", 1)).thenReturn("索引相关内容");
        // 模拟提示词模板渲染结果
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        // 配置 ChatClient 同步调用链：prompt -> system -> user -> call -> content
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("总结结果");

        String result = summaryTool.execute(context);

        // 断言返回结果与 ChatClient 返回的总结一致
        assertThat(result).isEqualTo("总结结果");
        // 验证 RagService 以显式主题和课程 ID 检索上下文
        verify(ragService).retrieveContext("数据库索引", 1);
    }

    /**
     * 测试场景：未显式提供 topic，需从用户消息中提取总结主题。
     * 准备条件：ToolContext 的 parameters 为空，消息文本包含“数据库范式”。
     * 断言意图：应从消息中解析出主题并调用 RAG 检索，最终返回 ChatClient 生成的总结。
     */
    @Test
    void shouldExtractTopicFromMessage() {
        ToolContext context = new ToolContext("帮我总结数据库范式", 2, null, null);
        // 模拟 RagService 对任意主题检索返回固定上下文
        when(ragService.retrieveContext(anyString(), any())).thenReturn("范式相关内容");
        // 模拟提示词模板渲染结果
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        // 配置 ChatClient 同步调用链
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("范式总结");

        String result = summaryTool.execute(context);

        // 断言返回结果与 ChatClient 返回的总结一致
        assertThat(result).isEqualTo("范式总结");
    }

    /**
     * 测试场景：通过 stream 方法获取流式总结。
     * 准备条件：构造包含总结请求的 ToolContext，并模拟 RAG 上下文、提示词模板与 ChatClient 流式调用链。
     * 断言意图：stream 应返回 ChatClient 流式接口输出的内容，并正常完成。
     */
    @Test
    void shouldReturnStreamFromExecute() {
        // 构造工具上下文：parameters 中显式指定 topic 为“索引”，课程 ID 为 3
        ToolContext context = new ToolContext("总结索引", 3, Map.of("topic", "索引"), null);
        // 模拟 RagService 检索上下文
        when(ragService.retrieveContext(anyString(), any())).thenReturn("上下文");
        // 模拟提示词模板渲染结果
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("提示");
        // 配置 ChatClient 流式调用链：prompt -> system -> user -> stream -> content
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("流式总结"));

        // 验证流式输出包含“流式总结”并正常完成
        StepVerifier.create(summaryTool.stream(context))
            .expectNext("流式总结")
            .verifyComplete();
    }

    /**
     * 测试场景：用户未提供可识别的总结主题。
     * 准备条件：消息与 parameters 中均不包含有效 topic。
     * 断言意图：validate 应返回 false，且校验错误信息中应提示“课程或章节”。
     */
    @Test
    void shouldRejectEmptyTopic() {
        ToolContext context = new ToolContext("总结一下", 4, null, null);

        // 断言参数校验不通过
        assertThat(summaryTool.validate(context)).isFalse();
        // 断言错误提示中包含“课程或章节”关键字
        assertThat(summaryTool.getValidationError(context)).contains("课程或章节");
    }
}
