package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.service.PlanService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// 复习计划控制器
@Validated
@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    // 生成复习计划
    @PostMapping
    public String createPlan(
            @RequestParam("course") @NotBlank(message = "课程名称不能为空") String course,
            @RequestParam("days") @NotBlank(message = "可用天数不能为空") String days) {
        return planService.createPlan(course, days);
    }

    // 生成复习计划（前端页面调用）
    @PostMapping("/generate")
    public String generatePlan(
            @RequestParam("courseName") @NotBlank(message = "课程名称不能为空") String courseName,
            @RequestParam(value = "days", defaultValue = "7天") String days) {
        return planService.createPlan(courseName, days);
    }
}
