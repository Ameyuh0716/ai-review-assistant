package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.common.SseUtils;
import com.aiservice.aireviewassistant.dto.SavePlanRequest;
import com.aiservice.aireviewassistant.entity.StudyPlan;
import com.aiservice.aireviewassistant.service.StudyPlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

/**
 * 学习计划控制器。
 * <p>保存并管理从对话生成的学习计划，支持保存、列表查询、详情查看与删除，
 * 基础路径为 /api/plans。</p>
 */
@Tag(name = "学习计划", description = "保存并管理从对话生成的学习计划")
@Validated
@RestController
@RequestMapping("/api/plans")
public class StudyPlanController {

    private final StudyPlanService studyPlanService;

    /**
     * 通过依赖注入构造控制器。
     *
     * @param studyPlanService 学习计划服务
     */
    public StudyPlanController(StudyPlanService studyPlanService) {
        this.studyPlanService = studyPlanService;
    }

    /**
     * 保存学习计划。
     * <p>POST /api/plans，请求体：{@code {"courseName":"计算机网络","availableDays":"7天","content":"..."}}</p>
     *
     * @param request 保存计划请求（课程名称、可用天数、计划正文）
     * @param currentUserId 当前用户 ID
     * @return 保存后的学习计划
     */
    @Operation(summary = "保存学习计划")
    @PostMapping
    public ApiResponse<StudyPlan> savePlan(
            @RequestBody @Validated SavePlanRequest request,
            @RequestAttribute("currentUserId") Integer currentUserId) {
        StudyPlan plan = studyPlanService.savePlan(
                currentUserId, request.getCourseName(), request.getAvailableDays(), request.getContent());
        return ApiResponse.success(plan);
    }

    /**
     * 查询当前用户的学习计划列表。
     * <p>GET /api/plans</p>
     *
     * @param currentUserId 当前用户 ID
     * @return 当前用户的学习计划列表
     */
    @Operation(summary = "查询当前用户的学习计划列表")
    @GetMapping
    public ApiResponse<List<StudyPlan>> listPlans(@RequestAttribute("currentUserId") Integer currentUserId) {
        return ApiResponse.success(studyPlanService.listByUserId(currentUserId));
    }

    /**
     * 查看学习计划详情。
     * <p>GET /api/plans/{id}，仅允许计划所有者访问。</p>
     *
     * @param id 学习计划主键
     * @param currentUserId 当前用户 ID
     * @return 学习计划详情；无权限或不存在返回 404
     */
    @Operation(summary = "查看学习计划详情")
    @GetMapping("/{id}")
    public ApiResponse<StudyPlan> getPlan(@PathVariable Integer id,
                                          @RequestAttribute("currentUserId") Integer currentUserId) {
        StudyPlan plan = studyPlanService.getById(id);
        // 校验计划存在且属于当前用户，否则返回无权访问
        if (plan == null || !plan.getUserId().equals(currentUserId)) {
            return ApiResponse.error(404, "计划不存在或无权访问");
        }
        plan.setDays(studyPlanService.parseDays(plan.getContent()));
        return ApiResponse.success(plan);
    }

    /**
     * 保存计划的结构化进度（每日勾选状态等）。
     * <p>PUT /api/plans/{id}/progress，请求体：{@code {"progress": "{\"1\":{\"done\":true}}"}}</p>
     *
     * @param id            学习计划主键
     * @param body          包含 progress 字符串的请求体
     * @param currentUserId 当前用户 ID
     * @return 更新后的计划；无权限或不存在返回 404
     */
    @Operation(summary = "保存计划结构化进度")
    @PutMapping("/{id}/progress")
    public ApiResponse<StudyPlan> updateProgress(@PathVariable Integer id,
                                                 @RequestBody Map<String, Object> body,
                                                 @RequestAttribute("currentUserId") Integer currentUserId) {
        Object progressObj = body.get("progress");
        String progress = progressObj != null ? String.valueOf(progressObj) : "{}";
        StudyPlan updated = studyPlanService.updateProgress(currentUserId, id, progress);
        if (updated == null) {
            return ApiResponse.error(404, "计划不存在或无权访问");
        }
        return ApiResponse.success(updated);
    }

    /**
     * 流式生成某天某环节的学习材料（复习内容 / 掌握内容 / 练习）。
     * <p>GET /api/plans/{id}/days/{day}/generate?section=review&token=xxx</p>
     * <p>生成完成后服务端自动将内容写入计划进度，前端刷新后仍可查看。</p>
     *
     * @param id            学习计划主键
     * @param day           天数序号（1 开始）
     * @param section       环节：review / mastery / practice
     * @param currentUserId 当前用户 ID
     * @return SSE 流式文本（逐行补空格以适配浏览器 SSE 解析）
     */
    @Operation(summary = "按天生成学习材料（复习/掌握/练习）")
    @GetMapping(value = "/{id}/days/{day}/generate", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> generateDaySection(@PathVariable Integer id,
                                           @PathVariable Integer day,
                                           @RequestParam(defaultValue = "review") String section,
                                           @RequestAttribute("currentUserId") Integer currentUserId) {
        return studyPlanService.generateDaySection(currentUserId, id, day, section)
            .map(SseUtils::padSseLines);
    }

    /**
     * 删除学习计划。
     * <p>DELETE /api/plans/{id}，仅允许计划所有者删除。</p>
     *
     * @param id 学习计划主键
     * @param currentUserId 当前用户 ID
     * @return 空响应；无权限或不存在返回 404
     */
    @Operation(summary = "删除学习计划")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable Integer id,
                                        @RequestAttribute("currentUserId") Integer currentUserId) {
        StudyPlan plan = studyPlanService.getById(id);
        // 校验计划存在且属于当前用户，否则返回无权访问
        if (plan == null || !plan.getUserId().equals(currentUserId)) {
            return ApiResponse.error(404, "计划不存在或无权访问");
        }
        studyPlanService.removeById(id);
        return ApiResponse.success(null);
    }
}
