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

// QuizTool 单元测试：验证参数提取、校验、execute/stream 调用
@ExtendWith(MockitoExtension.class)
class QuizToolTest {

    @Mock
    private QuizService quizService;

    private QuizTool quizTool;

    @BeforeEach
    void setUp() {
        quizTool = new QuizTool(quizService);
    }

    @Test
    void shouldExtractTopicFromExplicitParameters() {
        ToolContext context = new ToolContext("随便", null, Map.of("topic", "数据库事务", "count", "3"), null);
        when(quizService.generateQuiz("数据库事务", 3)).thenReturn("题目内容");

        String result = quizTool.execute(context);

        assertThat(result).isEqualTo("题目内容");
        verify(quizService).generateQuiz("数据库事务", 3);
    }

    @Test
    void shouldExtractTopicFromMessage() {
        ToolContext context = new ToolContext("出几道关于范式的题目", null, Map.of(), null);
        when(quizService.generateQuiz(anyString(), anyInt())).thenReturn("题目内容");

        quizTool.execute(context);

        verify(quizService).generateQuiz("范式", 3);
    }

    @Test
    void shouldDefaultCountToOne() {
        ToolContext context = new ToolContext("生成数据库索引的题目", null, Map.of("topic", "数据库索引"), null);
        when(quizService.generateQuiz("数据库索引", 1)).thenReturn("题目内容");

        quizTool.execute(context);

        verify(quizService).generateQuiz("数据库索引", 1);
    }

    @Test
    void shouldClampCountToMaxTen() {
        ToolContext context = new ToolContext("生成关于范式的20道题目", null, Map.of(), null);
        when(quizService.generateQuiz(anyString(), anyInt())).thenReturn("题目内容");

        quizTool.execute(context);

        verify(quizService).generateQuiz("范式", 10);
    }

    @Test
    void shouldValidateTopicNotEmpty() {
        ToolContext context = new ToolContext("生成题目", null, Map.of(), null);

        assertThat(quizTool.validate(context)).isFalse();
        assertThat(quizTool.getValidationError(context)).contains("知识点");
    }

    @Test
    void shouldStreamThroughService() {
        ToolContext context = new ToolContext("出几道关于范式的题目", null, Map.of(), null);
        when(quizService.generateQuizStream(anyString(), anyInt())).thenReturn(Flux.just("题", "目"));

        Flux<String> result = quizTool.stream(context);

        StepVerifier.create(result)
            .expectNext("题", "目")
            .verifyComplete();
    }
}
