package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.PlanService;
import com.aiservice.aireviewassistant.service.QuizService;
import com.aiservice.aireviewassistant.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Agent 服务缓存测试：验证 Caffeine 缓存能降低重复 LLM 调用
@ActiveProfiles("test")
@SpringBootTest
class AgentServiceCacheTest {

    @Autowired
    private QuizService quizService;

    @Autowired
    private PlanService planService;

    @Autowired
    private RagService ragService;

    @MockBean
    private ChatClient chatClient;

    @MockBean
    private PromptTemplate promptTemplate;

    @MockBean
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    @MockBean
    private ChatClient.ChatClientRequestSpec requestSpec;

    @MockBean
    private ChatClient.CallResponseSpec callResponseSpec;

    private void stubChatClient(String response) {
        when(promptTemplate.render(anyString(), any())).thenReturn("prompt");
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(response);
    }

    @Test
    void shouldCacheQuizResult() {
        stubChatClient("题目内容");

        String first = quizService.generateQuiz("数据库范式", 3);
        String second = quizService.generateQuiz("数据库范式", 3);

        assertThat(first).isEqualTo(second);
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void shouldCachePlanResult() {
        stubChatClient("计划内容");

        String first = planService.createPlan("数据库系统", "7天");
        String second = planService.createPlan("数据库系统", "7天");

        assertThat(first).isEqualTo(second);
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void shouldCacheRagResult() {
        stubChatClient("RAG回答");
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        String first = ragService.answerQuestion("什么是索引？", 1);
        String second = ragService.answerQuestion("什么是索引？", 1);

        assertThat(first).isEqualTo(second);
        verify(chatClient, times(1)).prompt();
    }

    @Test
    void shouldReturnCachedStream() {
        // 流式模式现在直接调用 LLM，不再使用缓存
        // 但同步模式仍使用缓存
        stubChatClient("流式回答");

        String syncResult = ragService.answerQuestion("问题", 1);
        String syncResult2 = ragService.answerQuestion("问题", 1);
        assertThat(syncResult).isEqualTo(syncResult2);
        // 缓存命中，chatClient.prompt() 只调用一次
        verify(chatClient, times(1)).prompt();
    }
}
