package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.aiservice.aireviewassistant.service.PlanService;
import com.aiservice.aireviewassistant.service.QuizService;
import com.aiservice.aireviewassistant.service.RagService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Agent 服务缓存与知识库隔离测试。
 * <p>一部分验证 {@link QuizService}、{@link PlanService} 的 Caffeine 缓存行为；
 * 另一部分验证 {@link RagService} 的多用户知识库隔离（检索范围限定）。</p>
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

    /**
     * 课程服务 mock。
     * <p>知识库检索需要用它把「用户」换算为「可见课程」，
     * 因此隔离测试必须能控制课程归属（包括“课程属于别人”这种越权场景）。</p>
     */
    @MockBean
    private CoursesService coursesService;

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
     * 测试场景：用户指定了一个<b>属于自己的</b>课程，然后在对话中提问。
     * <p>准备条件：向量库检索无结果。</p>
     * <p>断言意图：检索必须带上课程过滤条件，把搜索范围限制在该课程内。</p>
     */
    @Test
    void shouldScopeRagSearchToSpecifiedCourse() {
        stubChatClient("课程回答");
        Courses course = new Courses();
        course.setId(100);
        course.setUserId(7);
        when(coursesService.getById(100)).thenReturn(course);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());

        ragService.answerQuestion("什么是索引", 1, RagService.RagScope.of(7, 100));

        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore).similaritySearch(captor.capture());
        // 过滤表达式限定为 courseId == 100，避免搜到其它课程的资料
        assertThat(captor.getValue().getFilterExpression()).isNotNull();
        assertThat(captor.getValue().getFilterExpression().toString()).contains("courseId")
            .contains("100");
    }

    /**
     * 测试场景：用户 A 伪造了用户 B 的 courseId 发起提问。
     * <p>准备条件：该课程确实存在于库中，但归属于另一个用户。</p>
     * <p>断言意图：拒绝检索（不访问向量库），防止越权读取他人知识库。</p>
     */
    @Test
    void shouldRejectCourseNotOwnedByUser() {
        stubChatClient("通用回答");
        Courses othersCourse = new Courses();
        othersCourse.setId(100);
        othersCourse.setUserId(999);   // 属于别的用户
        when(coursesService.getById(100)).thenReturn(othersCourse);

        String answer = ragService.answerQuestion("什么是索引", 1, RagService.RagScope.of(7, 100));

        assertThat(answer).isEqualTo("通用回答");
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    /**
     * 测试场景：匿名用户（未登录）提问。
     * <p>准备条件：向量库中存在其它用户的资料分块。</p>
     * <p>断言意图：匿名用户没有任何可见课程，不应触碰向量库——
     * 这正是「我没添加知识库却命中别人资料」的修复点。</p>
     */
    @Test
    void shouldNotSearchForAnonymousUser() {
        stubChatClient("通用回答");

        String answer = ragService.answerQuestion("数据库的索引讲一下", 1, RagService.RagScope.NONE);

        assertThat(answer).isEqualTo("通用回答");
        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }
}
