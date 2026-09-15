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

/**
 * PlanTool 单元测试。
 * <p>
 * 测试目标：验证学习计划工具在参数给定、从消息中提取课程名与天数、使用默认值、
 * 参数校验失败以及流式生成等场景下的行为。
 */
@ExtendWith(MockitoExtension.class)
class PlanToolTest {

    @Mock
    private PlanService planService;

    private PlanTool planTool;

    @BeforeEach
    void setUp() {
        planTool = new PlanTool(planService);
    }

    /**
     * 测试场景：参数中已提供课程名与天数时直接生成计划。
     * <p>
     * 准备条件：ToolContext 参数包含 courseName 与 availableDays，mock PlanService 返回计划内容。
     * 断言意图：execute 应返回 PlanService 生成结果，并按参数调用 createPlan。
     */
    @Test
    void shouldExtractCourseFromParameters() {
        ToolContext context = new ToolContext("随便", null,
            Map.of("courseName", "数据库系统", "availableDays", "5天"), null);

        // mock 计划生成服务
        when(planService.createPlan("数据库系统", "5天")).thenReturn("计划内容");

        String result = planTool.execute(context);

        // 验证返回结果与服务调用参数
        assertThat(result).isEqualTo("计划内容");
        verify(planService).createPlan("数据库系统", "5天");
    }

    /**
     * 测试场景：参数未提供时从用户消息中提取课程名与天数。
     * <p>
     * 准备条件：ToolContext 消息包含课程名和天数，参数为空。
     * 断言意图：PlanService 应以提取出的“数据库系统”和“7天”被调用。
     */
    @Test
    void shouldExtractCourseAndDaysFromMessage() {
        ToolContext context = new ToolContext("帮我制定数据库系统的复习计划，7天", null, Map.of(), null);

        // mock 任意参数均返回计划内容
        when(planService.createPlan(anyString(), anyString())).thenReturn("计划内容");

        planTool.execute(context);

        // 验证从消息中提取的课程名与天数正确传递给服务
        verify(planService).createPlan("数据库系统", "7天");
    }

    /**
     * 测试场景：消息中未指定天数时，默认使用 7 天。
     * <p>
     * 准备条件：ToolContext 消息仅包含课程名，未提及天数。
     * 断言意图：PlanService 应以默认的“7天”被调用。
     */
    @Test
    void shouldDefaultDaysToSeven() {
        ToolContext context = new ToolContext("帮我制定软件工程的复习计划", null, Map.of(), null);

        // mock 服务对默认天数返回计划内容
        when(planService.createPlan("软件工程", "7天")).thenReturn("计划内容");

        planTool.execute(context);

        // 验证默认天数为 7 天
        verify(planService).createPlan("软件工程", "7天");
    }

    /**
     * 测试场景：未提供有效课程名时应拒绝执行。
     * <p>
     * 准备条件：ToolContext 消息仅包含“复习计划”，无法识别具体课程。
     * 断言意图：validate 应返回 false，且错误提示包含“课程”。
     */
    @Test
    void shouldRejectEmptyCourse() {
        ToolContext context = new ToolContext("复习计划", null, Map.of(), null);

        // 验证校验不通过且错误提示符合预期
        assertThat(planTool.validate(context)).isFalse();
        assertThat(planTool.getValidationError(context)).contains("课程");
    }

    /**
     * 测试场景：流式生成学习计划。
     * <p>
     * 准备条件：ToolContext 消息包含课程名，mock PlanService 流式返回分片数据。
     * 断言意图：stream 方法应返回按顺序发射的 Flux，并在结束时正常完成。
     */
    @Test
    void shouldStreamPlan() {
        ToolContext context = new ToolContext("帮我制定人工智能的复习计划", null, Map.of(), null);

        // mock 流式计划生成服务返回分片
        when(planService.createPlanStream(anyString(), anyString())).thenReturn(Flux.just("计", "划"));

        Flux<String> result = planTool.stream(context);

        // 验证流按预期顺序发射并正常完成
        StepVerifier.create(result)
            .expectNext("计", "划")
            .verifyComplete();
    }
}
