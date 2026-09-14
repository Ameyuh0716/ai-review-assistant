package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.service.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// PlanTool 单元测试：验证课程名/天数提取与校验
@ExtendWith(MockitoExtension.class)
class PlanToolTest {

    @Mock
    private PlanService planService;

    private PlanTool planTool;

    @BeforeEach
    void setUp() {
        planTool = new PlanTool(planService);
    }

    @Test
    void shouldExtractCourseFromParameters() {
        ToolContext context = new ToolContext("随便", null,
            Map.of("courseName", "数据库系统", "availableDays", "5天"), null);
        when(planService.createPlan("数据库系统", "5天")).thenReturn("计划内容");

        String result = planTool.execute(context);

        assertThat(result).isEqualTo("计划内容");
        verify(planService).createPlan("数据库系统", "5天");
    }

    @Test
    void shouldExtractCourseAndDaysFromMessage() {
        ToolContext context = new ToolContext("帮我制定数据库系统的复习计划，7天", null, Map.of(), null);
        when(planService.createPlan(anyString(), anyString())).thenReturn("计划内容");

        planTool.execute(context);

        verify(planService).createPlan("数据库系统", "7天");
    }

    @Test
    void shouldDefaultDaysToSeven() {
        ToolContext context = new ToolContext("帮我制定软件工程的复习计划", null, Map.of(), null);
        when(planService.createPlan("软件工程", "7天")).thenReturn("计划内容");

        planTool.execute(context);

        verify(planService).createPlan("软件工程", "7天");
    }

    @Test
    void shouldRejectEmptyCourse() {
        ToolContext context = new ToolContext("复习计划", null, Map.of(), null);

        assertThat(planTool.validate(context)).isFalse();
        assertThat(planTool.getValidationError(context)).contains("课程");
    }

    @Test
    void shouldStreamPlan() {
        ToolContext context = new ToolContext("帮我制定人工智能的复习计划", null, Map.of(), null);
        when(planService.createPlanStream(anyString(), anyString())).thenReturn(Flux.just("计", "划"));

        Flux<String> result = planTool.stream(context);

        StepVerifier.create(result)
            .expectNext("计", "划")
            .verifyComplete();
    }
}
