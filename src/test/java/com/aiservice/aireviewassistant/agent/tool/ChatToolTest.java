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

// ChatTool 单元测试：验证 prompt 构建与 ChatClient 调用
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

    @Test
    void shouldChatWithoutHistory() {
        ToolContext context = new ToolContext("你好", null, null, null);
        when(promptTemplate.render("chat-system.txt", null)).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("你好，同学！");

        String result = chatTool.execute(context);

        assertThat(result).isEqualTo("你好，同学！");
        verify(requestSpec).user("系统提示\n\n\n学生：你好\n助手：");
    }

    @Test
    void shouldChatWithHistory() {
        Message userMsg = new Message();
        userMsg.setRole("user");
        userMsg.setContent("之前的问题");
        Message assistantMsg = new Message();
        assistantMsg.setRole("assistant");
        assistantMsg.setContent("之前的回答");
        ToolContext context = new ToolContext("继续", null, null, List.of(userMsg, assistantMsg));

        when(promptTemplate.render("chat-system.txt", null)).thenReturn("系统提示");
        when(promptTemplate.render("chat-user-prefix.txt", null)).thenReturn("历史：\n");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("好的");

        chatTool.execute(context);

        verify(requestSpec).user("系统提示\n\n历史：\n学生：之前的问题\n助手：之前的回答\n\n学生：继续\n助手：");
    }

    @Test
    void shouldStreamChat() {
        ToolContext context = new ToolContext("你好", null, null, null);
        when(promptTemplate.render("chat-system.txt", null)).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("你", "好"));

        Flux<String> result = chatTool.stream(context);

        StepVerifier.create(result)
            .expectNext("你", "好")
            .verifyComplete();
    }
}
