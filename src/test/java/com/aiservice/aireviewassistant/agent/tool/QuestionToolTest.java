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

// QuestionTool 单元测试：验证问答转发到 RagService
@ExtendWith(MockitoExtension.class)
class QuestionToolTest {

    @Mock
    private RagService ragService;

    private QuestionTool questionTool;

    @BeforeEach
    void setUp() {
        questionTool = new QuestionTool(ragService);
    }

    @Test
    void shouldAnswerQuestion() {
        ToolContext context = new ToolContext("什么是索引？", 100, null, null);
        when(ragService.answerQuestion("什么是索引？", 100)).thenReturn("索引是...");

        String result = questionTool.execute(context);

        assertThat(result).isEqualTo("索引是...");
        verify(ragService).answerQuestion("什么是索引？", 100);
    }

    @Test
    void shouldStreamAnswer() {
        ToolContext context = new ToolContext("解释范式", 200, null, null);
        when(ragService.answerQuestionStream("解释范式", 200)).thenReturn(Flux.just("范", "式"));

        Flux<String> result = questionTool.stream(context);

        StepVerifier.create(result)
            .expectNext("范", "式")
            .verifyComplete();
    }
}
