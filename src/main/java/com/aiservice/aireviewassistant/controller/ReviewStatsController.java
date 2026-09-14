package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.dto.StudyProgressDto;
import com.aiservice.aireviewassistant.dto.UserProgressDto;
import com.aiservice.aireviewassistant.service.ReviewRecordsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

// 复习统计控制器：提供学习进度和统计数据
@Tag(name = "复习统计", description = "学习进度、连续复习、掌握度评分")
@RestController
@RequestMapping("/api/stats")
public class ReviewStatsController {

    private final ReviewRecordsService reviewRecordsService;

    public ReviewStatsController(ReviewRecordsService reviewRecordsService) {
        this.reviewRecordsService = reviewRecordsService;
    }

    // 获取用户整体学习进度
    @Operation(summary = "用户整体学习进度")
    @GetMapping("/overview")
    public ApiResponse<UserProgressDto> overview(
            @RequestAttribute(required = false) Integer currentUserId) {
        String userId = currentUserId != null ? String.valueOf(currentUserId) : "anonymous";
        return ApiResponse.success(reviewRecordsService.getUserOverallProgress(userId));
    }

    // 获取某课程的学习进度
    @Operation(summary = "课程学习进度")
    @GetMapping("/course/{courseId}")
    public ApiResponse<StudyProgressDto> courseProgress(
            @PathVariable Integer courseId,
            @RequestAttribute(required = false) Integer currentUserId) {
        String userId = currentUserId != null ? String.valueOf(currentUserId) : "anonymous";
        return ApiResponse.success(reviewRecordsService.getStudyProgress(courseId, userId));
    }
}
