package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.dto.StudyProgressDto;
import com.aiservice.aireviewassistant.dto.UserProgressDto;
import com.aiservice.aireviewassistant.service.ReviewRecordsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

/**
 * 复习统计控制器。
 * <p>面向前端提供学习进度 overview 与课程进度统计接口，基础路径为 /api/stats。</p>
 */
@Tag(name = "复习统计", description = "学习进度、连续复习、掌握度评分")
@RestController
@RequestMapping("/api/stats")
public class ReviewStatsController {

    private final ReviewRecordsService reviewRecordsService;

    /**
     * 通过依赖注入构造控制器。
     *
     * @param reviewRecordsService 复习记录服务
     */
    public ReviewStatsController(ReviewRecordsService reviewRecordsService) {
        this.reviewRecordsService = reviewRecordsService;
    }

    /**
     * 获取用户整体学习进度。
     * <p>GET /api/stats/overview</p>
     *
     * @param currentUserId 当前用户 ID（可选，由认证拦截器写入）
     * @return 用户整体学习进度
     */
    @Operation(summary = "用户整体学习进度")
    @GetMapping("/overview")
    public ApiResponse<UserProgressDto> overview(
            @RequestAttribute(required = false) Integer currentUserId) {
        // 未登录用户统一使用 anonymous 标识进行统计
        String userId = currentUserId != null ? String.valueOf(currentUserId) : "anonymous";
        return ApiResponse.success(reviewRecordsService.getUserOverallProgress(userId));
    }

    /**
     * 获取某课程的学习进度。
     * <p>GET /api/stats/course/{courseId}</p>
     *
     * @param courseId 课程 ID
     * @param currentUserId 当前用户 ID（可选，由认证拦截器写入）
     * @return 指定课程的学习进度
     */
    @Operation(summary = "课程学习进度")
    @GetMapping("/course/{courseId}")
    public ApiResponse<StudyProgressDto> courseProgress(
            @PathVariable Integer courseId,
            @RequestAttribute(required = false) Integer currentUserId) {
        // 未登录用户统一使用 anonymous 标识进行统计
        String userId = currentUserId != null ? String.valueOf(currentUserId) : "anonymous";
        return ApiResponse.success(reviewRecordsService.getStudyProgress(courseId, userId));
    }
}
