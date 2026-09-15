package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.service.QuizService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link QuizTool} 的单元测试。
 * 验证题目生成工具能否从用户消息或显式参数中正确提取知识点与题目数量，
 * 校验参数合法性，并将调用转发给 {@link QuizService} 的同步与流式接口。
 */
@ExtendWith(MockitoExtension.class)
class QuizToolTest {

    @Mock
    private QuizService quizService;

    private QuizTool quizTool;

    @BeforeEach
    void setUp() {
        // 初始化被测对象，注入模拟的 QuizService
        quizTool = new QuizTool(quizService);
    }

    /**
     * 测试场景：显式参数中已提供知识点与题目数量。
     * 准备条件：在 ToolContext 的 parameters 中设置 topic 与 count。
     * 断言意图：应优先使用显式参数调用 QuizService，并返回题目内容。
     */
    @Test
    void shouldExtractTopicFromExplicitParameters() {
        // 构造工具上下文：parameters 中显式指定 topic 为“数据库事务”，count 为 3
        ToolContext context = new ToolContext("随便", null, Map.of("topic", "数据库事务", "count", "3"), null);
        // 模拟 QuizService 生成对应主题与数量的题目
        when(quizService.generateQuiz("数据库事务", 3)).thenReturn("题目内容");

        String result = quizTool.execute(context);

        // 断言返回结果与模拟值一致
        assertThat(result).isEqualTo("题目内容");
        // 验证 QuizService.generateQuiz 被以显式参数调用
        verify(quizService).generateQuiz("数据库事务", 3);
    }

    /**
     * 测试场景：未提供显式 topic 参数，需从用户消息中提取知识点。
     * 准备条件：ToolContext 的 parameters 为空，消息文本包含“范式”。
     * 断言意图：应从消息中解析出知识点“范式”，并使用默认数量 3 调用服务。
     */
    @Test
    void shouldExtractTopicFromMessage() {
        ToolContext context = new ToolContext("出几道关于范式的题目", null, Map.of(), null);
        // 模拟 QuizService 对任意字符串/整数参数返回固定题目内容
        when(quizService.generateQuiz(anyString(), anyInt())).thenReturn("题目内容");

        quizTool.execute(context);

        // 验证服务被以从消息中提取的“范式”以及默认数量 3 调用
        verify(quizService).generateQuiz("范式", 3);
    }

    /**
     * 测试场景：显式提供 topic 但未提供 count。
     * 准备条件：parameters 中只有 topic，无 count。
     * 断言意图：题目数量应回退为默认值 1，并以该值调用 QuizService。
     */
    @Test
    void shouldDefaultCountToOne() {
        ToolContext context = new ToolContext("生成数据库索引的题目", null, Map.of("topic", "数据库索引"), null);
        // 模拟 QuizService 对 topic=数据库索引、count=1 的调用返回固定内容
        when(quizService.generateQuiz("数据库索引", 1)).thenReturn("题目内容");

        quizTool.execute(context);

        // 验证服务被以默认数量 1 调用
        verify(quizService).generateQuiz("数据库索引", 1);
    }

    /**
     * 测试场景：用户请求的题目数量超过系统允许的最大值。
     * 准备条件：消息中提到“20道题目”，parameters 为空。
     * 断言意图：题目数量应被截断为最大值 10，避免一次生成过多题目。
     */
    @Test
    void shouldClampCountToMaxTen() {
        ToolContext context = new ToolContext("生成关于范式的20道题目", null, Map.of(), null);
        // 模拟 QuizService 对任意参数返回固定题目内容
        when(quizService.generateQuiz(anyString(), anyInt())).thenReturn("题目内容");

        quizTool.execute(context);

        // 验证知识点被解析为“范式”，且数量被截断为上限 10
        verify(quizService).generateQuiz("范式", 10);
    }

    /**
     * 测试场景：用户未提供任何知识点。
     * 准备条件：消息与 parameters 中均不包含有效 topic。
     * 断言意图：validate 应返回 false，且校验错误信息中应提示“知识点”。
     */
    @Test
    void shouldValidateTopicNotEmpty() {
        ToolContext context = new ToolContext("生成题目", null, Map.of(), null);

        // 断言参数校验不通过
        assertThat(quizTool.validate(context)).isFalse();
        // 断言错误提示中包含“知识点”关键字
        assertThat(quizTool.getValidationError(context)).contains("知识点");
    }

    /**
     * 测试场景：通过 stream 方法流式生成题目。
     * 准备条件：构造包含题目生成请求的消息，并模拟 QuizService 返回字符流。
     * 断言意图：stream 应透传 QuizService 的流式输出，并按预期顺序完成。
     */
    @Test
    void shouldStreamThroughService() {
        ToolContext context = new ToolContext("出几道关于范式的题目", null, Map.of(), null);
        // 模拟 QuizService 流式接口返回“题”“目”两个片段
        when(quizService.generateQuizStream(anyString(), anyInt())).thenReturn(Flux.just("题", "目"));

        Flux<String> result = quizTool.stream(context);

        // 验证流式输出顺序为“题”“目”，并正常结束
        StepVerifier.create(result)
            .expectNext("题", "目")
            .verifyComplete();
    }
}
