package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.agent.tool.AgentTool;
import com.aiservice.aireviewassistant.agent.tool.ChainExecutor;
import com.aiservice.aireviewassistant.agent.tool.ToolContext;
import com.aiservice.aireviewassistant.agent.tool.ToolRegistry;
import com.aiservice.aireviewassistant.config.AppProperties;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_SELF;

/**
 * ReviewAssistantAgent 单元测试。
 * <p>覆盖智能助手的核心交互路径：空历史兜底、快速规则意图识别、
 * 带历史会话的 LLM 意图识别、工具参数校验、异常兜底、
 * 流式响应以及指标记录。</p>
 */
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
    private AppProperties appProperties;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    @InjectMocks
    private ReviewAssistantAgent agent;

    private Conversation conversation;

    /**
     * 每个测试方法执行前创建测试用会话。
     */
    @BeforeEach
    void setUp() {
        conversation = new Conversation();
        // 设置会话主键与用户标识，供后续逻辑使用
        conversation.setId(1);
        conversation.setUserId("default_user");
        // 学习统计配置：与 application.yml 默认一致（开启闲聊过滤）
        lenient().when(appProperties.getStats()).thenReturn(new AppProperties.Stats());
    }

    /**
     * 构造 Message 查询链的宽松 mock，避免空消息历史时出现未桩化的调用。
     *
     * @return 返回自引用的 {@link LambdaQueryChainWrapper} mock 对象
     */
    @SuppressWarnings("unchecked")
    private LambdaQueryChainWrapper<Message> mockMessageQuery() {
        // 使用 RETURNS_SELF 让所有链式方法返回自身，简化链式调用桩代码
        LambdaQueryChainWrapper<Message> wrapper = mock(LambdaQueryChainWrapper.class, RETURNS_SELF);
        // 宽松地桩化 MyBatis-Plus 查询链的各个环节
        lenient().when(messageService.lambdaQuery()).thenReturn(wrapper);
        lenient().when(wrapper.eq(any(SFunction.class), any())).thenReturn(wrapper);
        lenient().when(wrapper.orderByAsc(any(SFunction.class))).thenReturn(wrapper);
        lenient().when(wrapper.last(anyString())).thenReturn(wrapper);
        lenient().when(wrapper.list()).thenReturn(Collections.emptyList());
        lenient().when(wrapper.count()).thenReturn(0L);
        return wrapper;
    }

    /**
     * 测试场景：会话无历史消息且输入普通问候语。
     * <p>准备条件：消息查询返回空列表；注册表返回 CHAT 工具并校验通过。</p>
     * <p>断言意图：服务回退到 CHAT 工具执行，并返回工具执行结果。</p>
     */
    @Test
    void shouldFallbackToChatToolForEmptyMessage() {
        // 模拟消息历史为空
        mockMessageQuery();
        // 根据会话 ID 返回测试会话
        when(conversationService.getById(anyInt())).thenReturn(conversation);
        // 注册 CHAT 工具并使其校验通过
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("你好，同学！");

        String result = agent.chat("你好", 1);

        // 验证返回 CHAT 工具的执行结果
        assertThat(result).isEqualTo("你好，同学！");
        // 验证 CHAT 工具被实际执行
        verify(chatTool).execute(any(ToolContext.class));
    }

    /**
     * 测试场景：闲聊消息不应计入复习统计。
     * <p>准备条件：输入问候语"你好"，命中 CHAT 工具并正常执行。</p>
     * <p>断言意图：虽然对话正常完成，但复习记录服务不被调用，避免虚增复习次数与综合评分。</p>
     */
    @Test
    void shouldNotRecordReviewForCasualChat() {
        // 模拟消息历史为空
        mockMessageQuery();
        when(conversationService.getById(anyInt())).thenReturn(conversation);
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("你好！我可以帮你复习。");

        agent.chat("你好", 1);

        // 闲聊不应写入复习记录
        verify(reviewRecordsService, never()).saveFromAgent(any(), any(), any(), any());
    }

    /**
     * 测试场景：问候语变体同样被判为闲聊。
     * <p>准备条件：输入"谢谢老师"，命中 CHAT 工具。</p>
     * <p>断言意图：归一化后命中社交短语，复习记录不被写入。</p>
     */
    @Test
    void shouldNotRecordReviewForCasualVariant() {
        mockMessageQuery();
        when(conversationService.getById(anyInt())).thenReturn(conversation);
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("不客气～");

        agent.chat("谢谢老师", 1);

        verify(reviewRecordsService, never()).saveFromAgent(any(), any(), any(), any());
    }

    /**
     * 测试场景：含实质内容但被识别为普通对话的消息应计入复习统计。
     * <p>准备条件：带历史消息输入"继续"，LLM 返回 CHAT 意图。</p>
     * <p>断言意图：该消息不属于闲聊，复习记录正常写入（学习内容不被误过滤）。</p>
     */
    @Test
    void shouldRecordReviewForSubstantiveChat() {
        // 构造一条历史消息，触发 LLM 意图识别路径
        Message lastMessage = new Message();
        lastMessage.setRole("assistant");
        lastMessage.setContent("上一轮回答");
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
        when(chatTool.execute(any())).thenReturn("继续讲解的内容");

        agent.chat("继续", 2);

        // 非闲聊的普通对话应正常写入复习记录
        verify(reviewRecordsService, times(1))
            .saveFromAgent(any(), any(), eq("继续"), anyString());
    }

    /**
     * 测试场景：用户输入包含明显的测验意图关键词。
     * <p>准备条件：输入 "生成几道关于范式的题目"；QUIZ 工具可用且校验通过。</p>
     * <p>断言意图：通过快速规则直接匹配到 QUIZ 工具，并返回生成结果。</p>
     */
    @Test
    void shouldUseQuizIntentByFastRule() {
        // 模拟消息历史为空
        mockMessageQuery();
        // 模拟保存会话成功
        when(conversationService.save(any())).thenReturn(true);
        // 注册 QUIZ 工具并使其校验通过
        when(toolRegistry.getTool("QUIZ")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("题目");

        String result = agent.chat("生成几道关于范式的题目");

        // 验证返回 QUIZ 工具执行结果
        assertThat(result).isEqualTo("题目");
        // 验证通过快速规则获取了 QUIZ 工具
        verify(toolRegistry).getTool("QUIZ");
    }

    /**
     * 测试场景：会话已存在历史消息时，走 LLM 意图识别路径。
     * <p>准备条件：消息查询返回一条用户历史消息；LLM 返回 JSON 意图 "CHAT"；
     * CHAT 工具校验通过并返回固定回答。</p>
     * <p>断言意图：服务调用大模型进行意图识别，并根据识别结果调用 CHAT 工具。</p>
     */
    @Test
    void shouldUseLlmPathWhenConversationHasHistory() {
        // 构造一条用户历史消息
        Message lastMessage = new Message();
        lastMessage.setRole("user");
        lastMessage.setContent("之前的问题");
        lastMessage.setIntent("QUESTION");
        // 模拟消息查询返回该历史消息
        LambdaQueryChainWrapper<Message> wrapper = mockMessageQuery();
        when(wrapper.list()).thenReturn(List.of(lastMessage));
        // 根据会话 ID 返回测试会话
        when(conversationService.getById(2)).thenReturn(conversation);
        // 模拟工具 schema 描述
        when(toolRegistry.buildToolSchemas()).thenReturn("CHAT - 对话");
        // 使用 lenient 桩化 LLM 调用链，避免 UnusedStubbing 异常
        lenient().when(promptTemplate.render(anyString(), any(Map.class))).thenReturn("prompt");
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        // 模拟 LLM 返回 CHAT 意图的 JSON 结果
        lenient().when(callResponseSpec.content()).thenReturn("{\"intent\":\"CHAT\",\"parameters\":{}}");
        // 注册 CHAT 工具并使其校验通过
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("回答");

        String result = agent.chat("继续", 2);

        // 验证返回 CHAT 工具执行结果
        assertThat(result).isEqualTo("回答");
        // 验证 LLM 意图识别路径被触发
        verify(chatClient).prompt();
    }

    /**
     * 测试场景：命中工具但工具参数校验失败。
     * <p>准备条件：输入触发 QUIZ 工具，但工具校验不通过并返回错误信息。</p>
     * <p>断言意图：返回校验错误提示，且工具 execute 方法未被调用。</p>
     */
    @Test
    void shouldReturnValidationErrorWhenToolInvalid() {
        // 模拟消息历史为空
        mockMessageQuery();
        // 模拟保存会话成功
        when(conversationService.save(any())).thenReturn(true);
        // 注册 QUIZ 工具，但使其校验失败
        when(toolRegistry.getTool("QUIZ")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(false);
        when(chatTool.getValidationError(any())).thenReturn("请告诉我知识点");

        String result = agent.chat("生成题目");

        // 验证返回工具校验错误信息
        assertThat(result).isEqualTo("请告诉我知识点");
        // 验证工具 execute 从未被调用
        verify(chatTool, never()).execute(any());
    }

    /**
     * 测试场景：保存会话过程中发生异常。
     * <p>准备条件：conversationService.save 抛出 RuntimeException。</p>
     * <p>断言意图：服务捕获异常并返回包含 "抱歉" 的友好提示。</p>
     */
    @Test
    void shouldReturnFriendlyMessageOnException() {
        // 模拟保存会话时抛出运行时异常
        when(conversationService.save(any())).thenThrow(new RuntimeException("数据库异常"));

        String result = agent.chat("你好");

        // 验证返回友好的异常提示信息
        assertThat(result).contains("抱歉");
    }

    /**
     * 测试场景：流式对话接口正常返回内容。
     * <p>准备条件：保存会话后返回 conversationId=3；CHAT 工具校验通过并返回流式数据。</p>
     * <p>断言意图：返回的 Flux 首先推送会话 ID JSON，然后按顺序推送工具输出的数据块并正常完成。</p>
     */
    @Test
    void shouldStreamResponse() {
        // 模拟消息历史为空
        mockMessageQuery();
        // 模拟保存会话，并设置返回的会话 ID 为 3
        when(conversationService.save(any())).thenAnswer(inv -> {
            Conversation conv = inv.getArgument(0);
            conv.setId(3);
            return true;
        });
        // 注册 CHAT 工具并使其流式输出指定内容
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.stream(any())).thenReturn(Flux.just("你", "好"));

        Flux<String> result = agent.chatStream("你好");

        // 验证流式输出顺序：会话 ID -> "你" -> "好" -> 完成
        StepVerifier.create(result)
            .expectNext("{\"conversationId\":3}", "你", "好")
            .verifyComplete();
    }

    /**
     * 测试场景：流式对话接口发生异常。
     * <p>准备条件：保存会话时抛出 RuntimeException。</p>
     * <p>断言意图：返回的 Flux 仍正常完成，并推送包含 "抱歉" 的异常提示信息。</p>
     */
    @Test
    void shouldReturnErrorFluxOnStreamException() {
        // 模拟保存会话时抛出运行时异常
        when(conversationService.save(any())).thenThrow(new RuntimeException("异常"));

        Flux<String> result = agent.chatStream("你好");

        // 验证流中包含友好异常提示，并正常结束
        StepVerifier.create(result)
            .expectNextMatches(msg -> msg.contains("抱歉"))
            .verifyComplete();
    }

    /**
     * 测试场景：普通对话成功后记录指标。
     * <p>准备条件：CHAT 工具校验通过并正常返回。</p>
     * <p>断言意图：验证工具调用次数、请求耗时与请求成功状态均被正确记录。</p>
     */
    @Test
    void shouldRecordMetricsOnSuccessfulChat() {
        // 模拟消息历史为空
        mockMessageQuery();
        // 模拟保存会话成功
        when(conversationService.save(any())).thenReturn(true);
        // 注册 CHAT 工具，并设置工具名称
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.getName()).thenReturn("CHAT");
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.execute(any())).thenReturn("回答");

        agent.chat("你好");

        // 验证记录了 CHAT 工具调用
        verify(agentMetrics).incrementToolCall("CHAT");
        // 验证记录了请求耗时
        verify(agentMetrics).recordAgentRequest(eq("CHAT"), anyLong());
        // 验证记录了请求成功状态
        verify(agentMetrics).incrementAgentRequest(eq("CHAT"), eq(true));
    }

    /**
     * 测试场景：普通对话发生异常时记录指标。
     * <p>准备条件：保存会话时抛出 RuntimeException。</p>
     * <p>断言意图：即使发生异常，也应记录请求耗时，并将请求结果标记为失败。</p>
     */
    @Test
    void shouldRecordMetricsOnFailedChat() {
        // 模拟保存会话时抛出运行时异常
        when(conversationService.save(any())).thenThrow(new RuntimeException("数据库异常"));

        agent.chat("你好");

        // 验证仍记录了请求耗时
        verify(agentMetrics).recordAgentRequest(any(), anyLong());
        // 验证请求结果被标记为失败
        verify(agentMetrics).incrementAgentRequest(any(), eq(false));
    }

    /**
     * 测试场景：流式对话完成后记录指标。
     * <p>准备条件：保存会话返回 conversationId=3；CHAT 工具流式输出正常完成。</p>
     * <p>断言意图：流式响应正常结束后，验证工具调用次数、请求耗时与请求成功状态均被记录。</p>
     */
    @Test
    void shouldRecordMetricsOnStreamCompletion() {
        // 模拟消息历史为空
        mockMessageQuery();
        // 模拟保存会话并设置返回的会话 ID 为 3
        when(conversationService.save(any())).thenAnswer(inv -> {
            Conversation conv = inv.getArgument(0);
            conv.setId(3);
            return true;
        });
        // 注册 CHAT 工具并设置工具名称
        when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);
        when(chatTool.getName()).thenReturn("CHAT");
        when(chatTool.validate(any())).thenReturn(true);
        when(chatTool.stream(any())).thenReturn(Flux.just("你", "好"));

        // 验证流式输出正常完成
        StepVerifier.create(agent.chatStream("你好"))
            .expectNext("{\"conversationId\":3}", "你", "好")
            .verifyComplete();

        // 验证流式调用完成后记录了 CHAT 工具调用
        verify(agentMetrics).incrementToolCall("CHAT");
        // 验证记录了请求耗时
        verify(agentMetrics).recordAgentRequest(eq("CHAT"), anyLong());
        // 验证请求结果被标记为成功
        verify(agentMetrics).incrementAgentRequest(eq("CHAT"), eq(true));
    }
}
