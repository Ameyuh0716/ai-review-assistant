package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.StudyPlan;
import com.aiservice.aireviewassistant.service.StudyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// 学习计划控制器：保存、查看、删除从对话生成的计划
@Tag(name = "学习计划", description = "保存并管理从对话生成的学习计划")
@Validated
@RestController
@RequestMapping("/api/plans")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    @Operation(summary = "保存学习计划")
    @PostMapping
    public ApiResponse<StudyPlan> savePlan(
            @RequestParam("courseName") @NotBlank(message = "课程名称不能为空") String courseName,
            @RequestParam("availableDays") @NotBlank(message = "可用天数不能为空") String availableDays,
            @RequestBody @NotBlank(message = "计划内容不能为空") String content,
            @RequestAttribute("currentUserId") Integer currentUserId) {
        StudyPlan plan = studyPlanService.savePlan(currentUserId, courseName, availableDays, content);
        return ApiResponse.success(plan);
    }

    @Operation(summary = "查询当前用户的学习计划列表")
    @GetMapping
    public ApiResponse<List<StudyPlan>> listPlans(@RequestAttribute("currentUserId") Integer currentUserId) {
        return ApiResponse.success(studyPlanService.listByUserId(currentUserId));
    }

    @Operation(summary = "查看学习计划详情")
    @GetMapping("/{id}")
    public ApiResponse<StudyPlan> getPlan(@PathVariable Integer id,
                                          @RequestAttribute("currentUserId") Integer currentUserId) {
        StudyPlan plan = studyPlanService.getById(id);
        if (plan == null || !plan.getUserId().equals(currentUserId)) {
            return ApiResponse.error(404, "计划不存在或无权访问");
        }
        return ApiResponse.success(plan);
    }

    @Operation(summary = "删除学习计划")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable Integer id,
                                        @RequestAttribute("currentUserId") Integer currentUserId) {
        StudyPlan plan = studyPlanService.getById(id);
        if (plan == null || !plan.getUserId().equals(currentUserId)) {
            return ApiResponse.error(404, "计划不存在或无权访问");
        }
        studyPlanService.removeById(id);
        return ApiResponse.success(null);
    }
}
