package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.service.PlanService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 复习计划控制器。
 * <p>负责根据课程名称与可用天数生成 AI 复习计划，提供两个接口以兼容不同前端调用方式。</p>
 */
@Validated
@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private final PlanService planService;

    /**
     * 构造方法，注入复习计划服务。
     *
     * @param planService 复习计划生成服务
     */
    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    /**
     * 生成复习计划。
     * <p>HTTP: {@code POST /api/plan?course=xxx&days=7天}</p>
     *
     * @param course 课程名称，不能为空
     * @param days   可用天数描述，不能为空
     * @return AI 生成的复习计划文本
     */
    @PostMapping
    public String createPlan(
            @RequestParam("course") @NotBlank(message = "课程名称不能为空") String course,
            @RequestParam("days") @NotBlank(message = "可用天数不能为空") String days) {
        return planService.createPlan(course, days);
    }

    /**
     * 生成复习计划（前端页面调用）。
     * <p>HTTP: {@code POST /api/plan/generate?courseName=xxx&days=7天}</p>
     * <p>参数名与前端页面保持一致，内部复用 {@link PlanService#createPlan(String, String)}。</p>
     *
     * @param courseName 课程名称，不能为空
     * @param days       可用天数描述，默认 "7天"
     * @return AI 生成的复习计划文本
     */
    @PostMapping("/generate")
    public String generatePlan(
            @RequestParam("courseName") @NotBlank(message = "课程名称不能为空") String courseName,
            @RequestParam(value = "days", defaultValue = "7天") String days) {
        return planService.createPlan(courseName, days);
    }
}
