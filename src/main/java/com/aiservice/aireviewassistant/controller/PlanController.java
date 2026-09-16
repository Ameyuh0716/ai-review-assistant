package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.aiservice.aireviewassistant.service.PlanService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 复习计划控制器。
 * <p>负责根据课程名称与可用天数生成 AI 复习计划，提供两个接口以兼容不同前端调用方式。</p>
 */
@Validated
@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private final PlanService planService;
    private final CoursesService coursesService;

    /**
     * 构造方法，注入复习计划服务与课程服务。
     *
     * @param planService    复习计划生成服务
     * @param coursesService 课程查询服务
     */
    public PlanController(PlanService planService, CoursesService coursesService) {
        this.planService = planService;
        this.coursesService = coursesService;
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
    public ApiResponse<String> createPlan(
            @RequestParam("course") @NotBlank(message = "课程名称不能为空") String course,
            @RequestParam("days") @NotBlank(message = "可用天数不能为空") String days) {
        return ApiResponse.success(planService.createPlan(course, days));
    }

    /**
     * 生成复习计划（前端页面调用）。
     * <p>HTTP: {@code POST /api/plan/generate}</p>
     * <p>请求体：{@code {"courseId": 1, "days": 7}}</p>
     *
     * @param body 包含 courseId、days 的 JSON 请求体
     * @return AI 生成的复习计划文本
     */
    @PostMapping("/generate")
    public ApiResponse<String> generatePlan(@RequestBody Map<String, Object> body) {
        Object courseIdObj = body.get("courseId");
        if (courseIdObj == null) {
            return ApiResponse.error(400, "课程ID不能为空");
        }
        Integer courseId = Integer.valueOf(String.valueOf(courseIdObj));
        Courses course = coursesService.getById(courseId);
        if (course == null) {
            return ApiResponse.error(404, "课程不存在");
        }
        int days = 7;
        Object daysObj = body.get("days");
        if (daysObj != null) {
            try {
                days = Integer.parseInt(String.valueOf(daysObj));
            } catch (NumberFormatException ignored) {
                // 非法天数使用默认值 7
            }
        }
        String content = planService.createPlan(course.getName(), days + "天");
        return ApiResponse.success(content);
    }
}
