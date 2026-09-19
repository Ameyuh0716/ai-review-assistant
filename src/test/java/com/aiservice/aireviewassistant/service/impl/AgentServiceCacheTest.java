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

/**
 * Agent 服务缓存测试。
 * <p>验证 {@link QuizService}、{@link PlanService}、{@link RagService} 基于 Caffeine 的缓存行为，
 * 确保相同输入不会触发重复的底层 LLM 调用。</p>
 */
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

    /**
     * 快速模型客户端 mock。
     * <p>QuizService 注入的是 {@code fastChatClient}（出题使用快速模型），
     * 若不同时 mock，测试将穿透到真实 DashScope API（实测会卡住两分多钟后报错）。</p>
     */
    @MockBean(name = "fastChatClient")
    private ChatClient fastChatClient;

    @MockBean
    private PromptTemplate promptTemplate;

    @MockBean
    private org.springframework.ai.vectorstore.VectorStore vectorStore;

    @MockBean
    private ChatClient.ChatClientRequestSpec requestSpec;

    @MockBean
    private ChatClient.CallResponseSpec callResponseSpec;

    /**
     * 统一桩函数：模拟 ChatClient 调用链，固定返回指定的 LLM 内容。
     *
     * @param response 期望 ChatClient 返回的字符串
     */
    private void stubChatClient(String response) {
        stubChatClient(chatClient, response);
    }

    /**
     * 统一桩函数：模拟指定 ChatClient 的调用链，固定返回指定的 LLM 内容。
     *
     * @param client   目标 ChatClient（主客户端或快速客户端）
     * @param response 期望返回的字符串
     */
    private void stubChatClient(ChatClient client, String response) {
        // 固定提示词模板渲染结果，避免真实模板逻辑依赖
        when(promptTemplate.render(anyString(), any())).thenReturn("prompt");
        // 模拟 ChatClient 调用链：prompt() -> requestSpec -> system/user/call -> content
        when(client.prompt()).thenReturn(requestSpec);
        when(requestSpec.system(anyString())).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);
        when(callResponseSpec.content()).thenReturn(response);
    }

    /**
     * 测试场景：连续两次使用相同知识点与题量生成测验。
     * <p>准备条件：LLM 固定返回相同内容。</p>
     * <p>断言意图：两次返回结果一致，且底层 {@code chatClient.prompt()} 仅被调用一次，证明缓存生效。</p>
     */
    @Test
    void shouldCacheQuizResult() {
        // 出题走快速模型客户端（fastChatClient），需对其单独打桩
        stubChatClient(fastChatClient, "题目内容");

        // 第一次调用生成测验
        String first = quizService.generateQuiz("数据库范式", 3);
        // 第二次使用相同参数调用，应命中缓存
        String second = quizService.generateQuiz("数据库范式", 3);

        // 验证两次结果相同
        assertThat(first).isEqualTo(second);
        // 验证底层 LLM 仅被调用一次，缓存减少了重复请求
        verify(fastChatClient, times(1)).prompt();
    }

    /**
     * 测试场景：连续两次使用相同课程名与周期生成学习计划。
     * <p>准备条件：LLM 固定返回相同计划内容。</p>
     * <p>断言意图：两次返回结果一致，且底层 {@code chatClient.prompt()} 仅被调用一次，证明缓存生效。</p>
     */
    @Test
    void shouldCachePlanResult() {
        stubChatClient("计划内容");

        // 第一次调用生成学习计划
        String first = planService.createPlan("数据库系统", "7天");
        // 第二次使用相同参数调用，应命中缓存
        String second = planService.createPlan("数据库系统", "7天");

        // 验证两次结果相同
        assertThat(first).isEqualTo(second);
        // 验证底层 LLM 仅被调用一次
        verify(chatClient, times(1)).prompt();
    }

    /**
     * 测试场景：连续两次向 RAG 问答服务提出相同问题。
     * <p>准备条件：向量库未检索到相关文档，LLM 固定返回相同答案。</p>
     * <p>断言意图：两次返回结果一致，且 {@code chatClient.prompt()} 仅被调用一次。</p>
     */
    @Test
    void shouldCacheRagResult() {
        stubChatClient("RAG回答");
        // 模拟向量相似性搜索未返回任何文档
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        // 第一次问答
        String first = ragService.answerQuestion("什么是索引？", 1);
        // 第二次相同问题应命中缓存
        String second = ragService.answerQuestion("什么是索引？", 1);

        // 验证缓存命中后两次结果相同
        assertThat(first).isEqualTo(second);
        // 验证 LLM 调用次数为 1
        verify(chatClient, times(1)).prompt();
    }

    /**
     * 测试场景：验证同步 RAG 问答接口对相同问题的缓存效果。
     * <p>准备条件：当前流式接口直接调用 LLM、不使用缓存，因此本方法仅覆盖同步模式。</p>
     * <p>断言意图：两次同步调用返回相同结果，且底层 LLM 调用仅发生一次。</p>
     */
    @Test
    void shouldReturnCachedStream() {
        // 当前流式模式直接调用 LLM，不再使用缓存；本方法验证同步模式仍走缓存
        stubChatClient("流式回答");

        // 第一次同步问答
        String syncResult = ragService.answerQuestion("问题", 1);
        // 第二次相同问题应命中缓存
        String syncResult2 = ragService.answerQuestion("问题", 1);

        // 验证两次同步结果一致
        assertThat(syncResult).isEqualTo(syncResult2);
        // 缓存命中，chatClient.prompt() 仅被调用一次
        verify(chatClient, times(1)).prompt();
    }
}
