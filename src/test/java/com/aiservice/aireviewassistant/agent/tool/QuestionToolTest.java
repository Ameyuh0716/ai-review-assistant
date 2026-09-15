package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuestionTool} 的单元测试。
 * 验证问题工具能否正确将用户问题转发给 {@link RagService}，
 * 并覆盖普通执行与流式执行两种调用路径。
 */
@ExtendWith(MockitoExtension.class)
class QuestionToolTest {

    @Mock
    private RagService ragService;

    private QuestionTool questionTool;

    @BeforeEach
    void setUp() {
        // 初始化被测对象，注入模拟的 RagService
        questionTool = new QuestionTool(ragService);
    }

    /**
     * 测试场景：通过 execute 方法回答普通问题。
     * 准备条件：构造包含具体问题的 ToolContext，并模拟 RagService 返回固定答案。
     * 断言意图：execute 返回结果应与 RagService 返回值一致，并验证服务被以期望参数调用。
     */
    @Test
    void shouldAnswerQuestion() {
        // 构造工具上下文：用户问题为“什么是索引？”，课程 ID 为 100
        ToolContext context = new ToolContext("什么是索引？", 100, null, null);
        // 模拟 RagService 对应该问题的返回结果
        when(ragService.answerQuestion("什么是索引？", 100)).thenReturn("索引是...");

        String result = questionTool.execute(context);

        // 断言返回结果与模拟值一致
        assertThat(result).isEqualTo("索引是...");
        // 验证 RagService.answerQuestion 被以期望参数调用过一次
        verify(ragService).answerQuestion("什么是索引？", 100);
    }

    /**
     * 测试场景：通过 stream 方法获取流式回答。
     * 准备条件：构造包含流式问题的 ToolContext，并模拟 RagService 返回字符流。
     * 断言意图：流式输出应依次包含预期的字符，并最终正常完成。
     */
    @Test
    void shouldStreamAnswer() {
        ToolContext context = new ToolContext("解释范式", 200, null, null);
        // 模拟 RagService 返回包含两个字符的 Flux 流
        when(ragService.answerQuestionStream("解释范式", 200)).thenReturn(Flux.just("范", "式"));

        Flux<String> result = questionTool.stream(context);

        // 使用 StepVerifier 验证流依次输出“范”“式”后正常完成
        StepVerifier.create(result)
            .expectNext("范", "式")
            .verifyComplete();
    }
}
