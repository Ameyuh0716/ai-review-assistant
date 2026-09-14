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

// SummaryTool 单元测试：验证总结主题提取与 RAG 上下文调用
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
        summaryTool = new SummaryTool(chatClient, ragService, promptTemplate);
    }

    @Test
    void shouldGenerateSummaryWithTopicParameter() {
        ToolContext context = new ToolContext("随便", 1, Map.of("topic", "数据库索引"), null);
        when(ragService.retrieveContext("数据库索引", 1)).thenReturn("索引相关内容");
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("总结结果");

        String result = summaryTool.execute(context);

        assertThat(result).isEqualTo("总结结果");
        verify(ragService).retrieveContext("数据库索引", 1);
    }

    @Test
    void shouldExtractTopicFromMessage() {
        ToolContext context = new ToolContext("帮我总结数据库范式", 2, null, null);
        when(ragService.retrieveContext(anyString(), any())).thenReturn("范式相关内容");
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("范式总结");

        String result = summaryTool.execute(context);

        assertThat(result).isEqualTo("范式总结");
    }

    @Test
    void shouldReturnStreamFromExecute() {
        ToolContext context = new ToolContext("总结索引", 3, Map.of("topic", "索引"), null);
        when(ragService.retrieveContext(anyString(), any())).thenReturn("上下文");
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("流式总结"));

        StepVerifier.create(summaryTool.stream(context))
            .expectNext("流式总结")
            .verifyComplete();
    }

    @Test
    void shouldRejectEmptyTopic() {
        ToolContext context = new ToolContext("总结一下", 4, null, null);

        assertThat(summaryTool.validate(context)).isFalse();
        assertThat(summaryTool.getValidationError(context)).contains("课程或章节");
    }
}
