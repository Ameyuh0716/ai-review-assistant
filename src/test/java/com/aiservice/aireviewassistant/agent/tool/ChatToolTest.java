package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.entity.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ChatTool 单元测试。
 * <p>
 * 测试目标：验证聊天工具在无历史消息、有历史消息以及流式响应场景下，
 * 能否正确渲染 prompt 并通过 ChatClient 发起调用。
 */
@ExtendWith(MockitoExtension.class)
class ChatToolTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private PromptTemplate promptTemplate;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @Mock
    private ChatClient.StreamResponseSpec streamResponseSpec;

    private ChatTool chatTool;

    @BeforeEach
    void setUp() {
        chatTool = new ChatTool(chatClient, promptTemplate);
    }

    /**
     * 测试场景：无历史消息时进行普通对话。
     * <p>
     * 准备条件：构造无历史记录的 ToolContext，mock 系统提示与 ChatClient 调用链路。
     * 断言意图：返回内容应与 ChatClient 返回结果一致，且最终 user prompt 按预期格式组装。
     */
    @Test
    void shouldChatWithoutHistory() {
        ToolContext context = new ToolContext("你好", null, null, null);

        // mock 系统提示渲染与 ChatClient 调用链
        when(promptTemplate.render("chat-system.txt", null)).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("你好，同学！");

        String result = chatTool.execute(context);

        // 验证返回结果与 prompt 内容
        assertThat(result).isEqualTo("你好，同学！");
        verify(requestSpec).user("系统提示\n\n\n学生：你好\n助手：");
    }

    /**
     * 测试场景：携带历史消息时进行多轮对话。
     * <p>
     * 准备条件：构造包含两条历史消息的 ToolContext，mock 系统提示与历史前缀模板。
     * 断言意图：最终 user prompt 应将历史消息按角色交替拼接在当前问题之前。
     */
    @Test
    void shouldChatWithHistory() {
        // 构造用户与助手的历史消息
        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent("之前的问题");
        Message assistantMsg = new Message();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("之前的回答");
        ToolContext context = new ToolContext("继续", null, null, List.of(userMsg, assistantMsg));

        // mock 模板渲染与 ChatClient 调用链
        when(promptTemplate.render("chat-system.txt", null)).thenReturn("系统提示");
        when(promptTemplate.render("chat-user-prefix.txt", null)).thenReturn("历史：\n");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("好的");

        chatTool.execute(context);

        // 验证最终 prompt 包含历史消息与当前问题
        verify(requestSpec).user("系统提示\n\n历史：\n学生：之前的问题\n助手：之前的回答\n\n学生：继续\n助手：");
    }

    /**
     * 测试场景：流式聊天响应。
     * <p>
     * 准备条件：构造无历史记录的 ToolContext，mock stream 调用返回分片数据。
     * 断言意图：应返回按顺序发射的 Flux 字符串流，并在结束时正常完成。
     */
    @Test
    void shouldStreamChat() {
        ToolContext context = new ToolContext("你好", null, null, null);

        // mock stream 模式下的 ChatClient 调用链
        when(promptTemplate.render("chat-system.txt", null)).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("你", "好"));

        Flux<String> result = chatTool.stream(context);

        // 验证流按预期顺序发射并正常完成
        StepVerifier.create(result)
            .expectNext("你", "好")
            .verifyComplete();
    }
}
