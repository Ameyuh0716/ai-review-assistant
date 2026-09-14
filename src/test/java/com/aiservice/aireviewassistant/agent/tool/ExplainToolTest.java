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

// ExplainTool 单元测试：验证概念提取与解释生成
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

    @Test
    void shouldExplainConceptParameter() {
        ToolContext context = new ToolContext("随便", 1, Map.of("concept", "第三范式"), null);
        when(ragService.retrieveContext("第三范式", 1)).thenReturn("范式相关内容");
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("系统提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn("第三范式解释");

        String result = explainTool.execute(context);

        assertThat(result).isEqualTo("第三范式解释");
        verify(ragService).retrieveContext("第三范式", 1);
    }

    @Test
    void shouldExtractConceptFromMessage() {
        ToolContext context = new ToolContext("解释一下数据库索引", 2, null, null);
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

    @Test
    void shouldReturnStreamFromExecute() {
        ToolContext context = new ToolContext("解释 B+树", 3, Map.of("concept", "B+树"), null);
        when(ragService.retrieveContext(anyString(), any())).thenReturn("上下文");
        when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("提示");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.stream()).thenReturn(streamResponseSpec);
        when(streamResponseSpec.content()).thenReturn(Flux.just("B+树解释"));

        StepVerifier.create(explainTool.stream(context))
            .expectNext("B+树解释")
            .verifyComplete();
    }

    @Test
    void shouldRejectEmptyConcept() {
        ToolContext context = new ToolContext("解释一下", 4, null, null);

        assertThat(explainTool.validate(context)).isFalse();
        assertThat(explainTool.getValidationError(context)).contains("概念或题目");
    }
}
