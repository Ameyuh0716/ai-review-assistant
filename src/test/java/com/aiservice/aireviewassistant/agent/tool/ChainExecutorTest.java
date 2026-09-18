package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * ChainExecutor 单元测试。
 * <p>
 * 测试目标：验证链式请求的识别、LLM 步骤拆解与规则兜底、主题提取质量，
 * 以及步骤间主题继承与降级处理。
 */
@ExtendWith(MockitoExtension.class)
class ChainExecutorTest {

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private AgentTool summaryTool;

    @Mock
    private AgentTool quizTool;

    @Mock
    private AgentTool chatTool;

    @Mock
    private AgentTool planTool;

    @Mock
    private ChatClient chatClient;

    @Mock
    private PromptTemplate promptTemplate;

    @Mock
    private ChatClient.ChatClientRequestSpec requestSpec;

    @Mock
    private ChatClient.CallResponseSpec callResponseSpec;

    /** 使用真实 ObjectMapper，让 JSON 解析逻辑得到实际校验 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChainExecutor chainExecutor;

    @BeforeEach
    void setUp() {
        // 初始化被测对象
        chainExecutor = new ChainExecutor(toolRegistry, chatClient, promptTemplate, objectMapper);

        // 注册常用的 mock 工具，避免每个测试重复配置；使用 lenient 防止未引用时报错
        lenient().when(toolRegistry.getTool("SUMMARY")).thenReturn(summaryTool);
        lenient().when(toolRegistry.getTool("QUIZ")).thenReturn(quizTool);
        lenient().when(toolRegistry.getTool("PLAN")).thenReturn(planTool);
        lenient().when(toolRegistry.getTool("CHAT")).thenReturn(chatTool);

        // 配置 mock 工具的名称与校验行为
        lenient().when(summaryTool.getName()).thenReturn("SUMMARY");
        lenient().when(quizTool.getName()).thenReturn("QUIZ");
        lenient().when(planTool.getName()).thenReturn("PLAN");
        lenient().when(chatTool.getName()).thenReturn("CHAT");
        lenient().when(summaryTool.validate(any())).thenReturn(true);
        lenient().when(quizTool.validate(any())).thenReturn(true);
        lenient().when(planTool.validate(any())).thenReturn(true);
        lenient().when(chatTool.validate(any())).thenReturn(true);

        // 默认让 LLM 返回空内容，从而走规则兜底路径；验证 LLM 路径的用例会单独覆盖返回值
        lenient().when(toolRegistry.buildToolSchemas()).thenReturn("可用工具：SUMMARY、QUIZ、PLAN");
        lenient().when(promptTemplate.render(anyString(), any())).thenReturn("规划提示词");
        lenient().when(chatClient.prompt()).thenReturn(requestSpec);
        lenient().when(requestSpec.system(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.user(anyString())).thenReturn(requestSpec);
        lenient().when(requestSpec.call()).thenReturn(callResponseSpec);
        lenient().when(callResponseSpec.content()).thenReturn("");
    }

    /**
     * 测试场景：判断用户输入是否包含多个连续意图。
     * <p>
     * 准备条件：提供包含“先…再…”的复合句、无“先”但含两种动作的复合句，以及单一意图的简单句。
     * 断言意图：复合句应被识别为链式请求，简单句不应被识别。
     */
    @Test
    void shouldDetectChainRequest() {
        assertThat(chainExecutor.isChainRequest("先总结第三章，再出3道题")).isTrue();
        assertThat(chainExecutor.isChainRequest("总结第三章")).isFalse();
        // 没有“先”但同时包含总结与出题两种动作，也属于多步请求
        assertThat(chainExecutor.isChainRequest("总结一下操作系统，然后出几道题")).isTrue();
        // 单一动作的追问不应误判为链式请求（需保留“复用上一轮主题”的行为）
        assertThat(chainExecutor.isChainRequest("再来几道题")).isFalse();
    }

    /**
     * 测试场景：解析“总结 + 出题”的链式请求（规则兜底路径）。
     * <p>
     * 准备条件：输入包含明确主题与题目数量。
     * 断言意图：应解析出两个步骤，第一步为 SUMMARY 并提取 topic，第二步为 QUIZ 并提取 count。
     */
    @Test
    void shouldParseSummaryThenQuizChain() {
        List<ChainExecutor.ChainStep> steps = chainExecutor.parseChain("先总结第三章，再出3道题");

        // 验证步骤数量与每一步的意图、参数
        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).intent()).isEqualTo("SUMMARY");
        assertThat(steps.get(0).parameters().get("topic")).isEqualTo("第三章");
        assertThat(steps.get(1).intent()).isEqualTo("QUIZ");
        assertThat(steps.get(1).parameters().get("count")).isEqualTo("3");
    }

    /**
     * 测试场景：含糊的祈使句不应被当作学科名（回归测试）。
     * <p>
     * 历史 Bug：“再出几道题”会把主题提取为“出几道题”，
     * “给我设计一个10天的复习计划”会把课程名提取为“设计一个的复习”，
     * 导致生成与主题无关的题目/计划。
     * 断言意图：提取不到主题时应返回空串（交由上层继承），而非返回噪声文本。
     */
    @Test
    void shouldNotTreatImperativePhraseAsTopic() {
        List<ChainExecutor.ChainStep> steps = chainExecutor
                .parseChain("先帮我总结一下操作系统的复习内容，然后再出几道题，然后给我设计一个10天的复习计划");

        assertThat(steps).hasSize(3);
        // 第一步能正确提取出真正的主题
        assertThat(steps.get(0).parameters().get("topic")).isEqualTo("操作系统");
        // 后两步未指定主题，应留空以便继承
        assertThat(steps.get(1).parameters().get("topic")).isEmpty();
        assertThat(steps.get(2).parameters().get("courseName")).isEmpty();
        // 天数应被正确提取
        assertThat(steps.get(2).parameters().get("availableDays")).isEqualTo("10天");
    }

    /**
     * 测试场景：消息中的“先”不在开头时也应能正确切分。
     */
    @Test
    void shouldParseChainWhenXianIsNotAtStart() {
        List<ChainExecutor.ChainStep> steps = chainExecutor.parseChain("帮我先总结操作系统再出2道题");

        assertThat(steps).hasSize(2);
        assertThat(steps.get(0).parameters().get("topic")).isEqualTo("操作系统");
        assertThat(steps.get(1).parameters().get("count")).isEqualTo("2");
    }

    /**
     * 测试场景：链式执行时，后续步骤应继承前面步骤提取的主题。
     * <p>
     * 准备条件：mock SUMMARY 与 QUIZ 工具返回包含主题的结果。
     * 断言意图：最终返回结果中应同时包含两个步骤的输出内容。
     */
    @Test
    void shouldInheritTopicAcrossSteps() {
        // 模拟工具执行结果
        when(summaryTool.execute(any())).thenReturn("第三章总结内容");
        when(quizTool.execute(any())).thenReturn("第三章题目");

        ChainExecutor.ChainExecution execution =
                chainExecutor.execute("先总结第三章，再出2道题", 1, null);

        // 验证链式执行结果包含各步骤输出
        assertThat(execution.text()).contains("第三章总结内容");
        assertThat(execution.text()).contains("第三章题目");
        // 第二步未指定主题，应继承第一步的主题
        ArgumentCaptor<ToolContext> captor = ArgumentCaptor.forClass(ToolContext.class);
        org.mockito.Mockito.verify(quizTool).execute(captor.capture());
        assertThat(captor.getValue().getParameters().get("topic")).isEqualTo("第三章");
    }

    /**
     * 测试场景：LLM 拆解路径下，未指定主题的步骤也应继承上一步主题。
     * <p>
     * 准备条件：mock LLM 返回包含空 topic 的步骤数组（模拟提示词要求的“留空由系统继承”）。
     * 断言意图：QUIZ 步骤实际收到的 topic 应为第一步的主题。
     */
    @Test
    void shouldInheritTopicFromLlmDecomposedSteps() {
        when(callResponseSpec.content()).thenReturn("""
                [
                  {"intent": "SUMMARY", "parameters": {"topic": "操作系统"}},
                  {"intent": "QUIZ", "parameters": {"topic": "", "count": ""}}
                ]
                """);
        when(summaryTool.execute(any())).thenReturn("操作系统总结");
        when(quizTool.execute(any())).thenReturn("操作系统题目");
        ChainExecutor.ChainExecution execution =
                chainExecutor.execute("先总结操作系统，再出几道题", 1, null);

        assertThat(execution.stepResults()).hasSize(2);
        assertThat(execution.stepResults().get(0).intent()).isEqualTo("SUMMARY");
        assertThat(execution.stepResults().get(1).intent()).isEqualTo("QUIZ");
        // 主题应被继承到 QUIZ 步骤
        assertThat(execution.stepResults().get(1).parameters().get("topic")).isEqualTo("操作系统");
    }

    /**
     * 测试场景：LLM 返回非法 JSON 时应回退到规则拆解。
     */
    @Test
    void shouldFallbackToRulesWhenLlmReturnsInvalidJson() {
        when(callResponseSpec.content()).thenReturn("抱歉，我无法理解这个请求");
        when(summaryTool.execute(any())).thenReturn("总结内容");
        when(quizTool.execute(any())).thenReturn("题目内容");

        ChainExecutor.ChainExecution execution =
                chainExecutor.execute("先总结第三章，再出2道题", 1, null);

        assertThat(execution.stepResults()).hasSize(2);
        assertThat(execution.text()).contains("总结内容");
    }

    /**
     * 测试场景：输入无法解析为有效链式步骤时的降级处理。
     * <p>
     * 准备条件：提供不含明确意图与参数的模糊输入。
     * 断言意图：执行结果中应包含“未能识别”提示信息。
     */
    @Test
    void shouldReturnEmptyResultForUnparsableChain() {
        ChainExecutor.ChainExecution execution = chainExecutor.execute("先再然后", 1, null);

        assertThat(execution.text()).contains("未能识别");
        assertThat(execution.stepResults()).isEmpty();
    }
}
