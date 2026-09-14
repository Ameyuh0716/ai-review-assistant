package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.agent.tool.AgentTool;
import com.aiservice.aireviewassistant.agent.tool.ChainExecutor;
import com.aiservice.aireviewassistant.agent.tool.ToolContext;
import com.aiservice.aireviewassistant.agent.tool.ToolRegistry;
import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.entity.Message;
import com.aiservice.aireviewassistant.metrics.AgentMetrics;
import com.aiservice.aireviewassistant.service.AgentLogService;
import com.aiservice.aireviewassistant.service.ConversationService;
import com.aiservice.aireviewassistant.service.MessageService;
import com.aiservice.aireviewassistant.service.ReviewRecordsService;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.conditions.query.LambdaQueryChainWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_SELF;

// ReviewAssistantAgent 单元测试：覆盖意图识别、工具调度、流式响应
@ExtendWith(MockitoExtension.class)
class ReviewAssistantAgentTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ConversationService conversationService;

    @Mock
    private MessageService messageService;

    @Mock
    private AgentLogService agentLogService;

    @Mock
    private ReviewRecordsService reviewRecordsService;

    @Mock
    private PromptTemplate promptTemplate;

    @Mock
    private AgentTool chatTool;

    @Mock
    private AgentMetrics agentMetrics;

    @Mock
    private ChainExecutor chainExecutor;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @InjectMocks
    private ReviewAssistantAgent agent;

    private Conversation conversation;

    @BeforeEach
    void setUp() {
        conversation = new Conversation();
        conversation.setId(1);
        conversation.setUserId("default_user");
    }

    @SuppressWarnings("unchecked")
    private LambdaQueryChainWrapper<Message> mockMessageQuery() {
        LambdaQueryChainWrapper<Message> wrapper = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        lenient().when(messageService.lambdaQuery()).thenReturn(wrapper);
        lenient().when(wrapper.eq(any(SFunction.class), any())).thenReturn(wrapper);
        lenient().when(wrapper.orderByAsc(any(SFunction.class))).thenReturn(wrapper);
        lenient().when(wrapper.last(anyString())).thenReturn(wrapper);
        lenient().when(wrapper.list()).thenReturn(Collections.emptyList());
        lenient().when(wrapper.count()).thenReturn(0L);
        return wrapper;
    }

    @Test
    void shouldFallbackToChatToolForEmptyMessage() {
        mockMessageQuery();
        when(conversationService.getById(anyInt())).thenReturn(conversation);
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("你好，同学！");

        String result = agent.chat("你好", 1);

        assertThat(result).isEqualTo("你好，同学！");
        verify(chatTool).execute(any(ToolContext.class));
    }

    @Test
    void shouldUseQuizIntentByFastRule() {
        mockMessageQuery();
        when(conversationService.save(any())).thenReturn(true);
        when(toolRegistry.getTool("QUIZ")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("题目");

        String result = agent.chat("生成几道关于范式的题目");

        assertThat(result).isEqualTo("题目");
        verify(toolRegistry).getTool("QUIZ");
    }

    @Test
    void shouldUseLlmPathWhenConversationHasHistory() {
        Message lastMessage = new Message();
        lastMessage.setRole("user");
        lastMessage.setContent("之前的问题");
        lastMessage.setIntent("QUESTION");
        LambdaQueryChainWrapper<Message> wrapper = mockMessageQuery();
        when(wrapper.list()).thenReturn(List.of(lastMessage));
        when(conversationService.getById(2)).thenReturn(conversation);
        when(toolRegistry.buildToolSchemas()).thenReturn("CHAT - 对话");
        lenient().when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("prompt");
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        lenient().when(callResponseSpec.content()).thenReturn("{\"intent\":\"CHAT\",\"parameters\":{}}");
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("回答");

        String result = agent.chat("继续", 2);

        assertThat(result).isEqualTo("回答");
        verify(chatClient).prompt();
    }

    @Test
    void shouldReturnValidationErrorWhenToolInvalid() {
        mockMessageQuery();
        when(conversationService.save(any())).thenReturn(true);
        when(toolRegistry.getTool("QUIZ")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(false);
        when(chatTool.getValidationError(any())).thenReturn("请告诉我知识点");

        String result = agent.chat("生成题目");

        assertThat(result).isEqualTo("请告诉我知识点");
        verify(chatTool, never()).execute(any());
    }

    @Test
    void shouldReturnFriendlyMessageOnException() {
        when(conversationService.save(any())).thenThrow(new RuntimeException("数据库异常"));

        String result = agent.chat("你好");

        assertThat(result).contains("抱歉");
    }

    @Test
    void shouldStreamResponse() {
        mockMessageQuery();
        when(conversationService.save(any())).thenAnswer(inv -> {
            Conversation conv = inv.getArgument(0);
            conv.setId(3);
            return true;
        });
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.stream(any())).thenReturn(Flux.just("你", "好"));

        Flux<String> result = agent.chatStream("你好");

        StepVerifier.create(result)
            .expectNext("{\"conversationId\":3}", "你", "好")
            .verifyComplete();
    }

    @Test
    void shouldReturnErrorFluxOnStreamException() {
        when(conversationService.save(any())).thenThrow(new RuntimeException("异常"));

        Flux<String> result = agent.chatStream("你好");

        StepVerifier.create(result)
            .expectNextMatches(msg -> msg.contains("抱歉"))
            .verifyComplete();
    }

    @Test
    void shouldRecordMetricsOnSuccessfulChat() {
        mockMessageQuery();
        when(conversationService.save(any())).thenReturn(true);
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.getName()).thenReturn("CHAT");
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("回答");

        agent.chat("你好");

        verify(agentMetrics).incrementToolCall("CHAT");
        verify(agentMetrics).recordAgentRequest(eq("CHAT"), anyLong());
        verify(agentMetrics).incrementAgentRequest(eq("CHAT"), eq(true));
    }

    @Test
    void shouldRecordMetricsOnFailedChat() {
        when(conversationService.save(any())).thenThrow(new RuntimeException("数据库异常"));

        agent.chat("你好");

        verify(agentMetrics).recordAgentRequest(any(), anyLong());
        verify(agentMetrics).incrementAgentRequest(any(), eq(false));
    }

    @Test
    void shouldRecordMetricsOnStreamCompletion() {
        mockMessageQuery();
        when(conversationService.save(any())).thenAnswer(inv -> {
            Conversation conv = inv.getArgument(0);
            conv.setId(3);
            return true;
        });
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.getName()).thenReturn("CHAT");
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.stream(any())).thenReturn(Flux.just("你", "好"));

        StepVerifier.create(agent.chatStream("你好"))
            .expectNext("{\"conversationId\":3}", "你", "好")
            .verifyComplete();

        verify(agentMetrics).incrementToolCall("CHAT");
        verify(agentMetrics).recordAgentRequest(eq("CHAT"), anyLong());
        verify(agentMetrics).incrementAgentRequest(eq("CHAT"), eq(true));
    }
}
