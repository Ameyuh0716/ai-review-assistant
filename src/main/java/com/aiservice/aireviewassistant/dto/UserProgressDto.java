package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 用户整体学习进度总览
@Getter
@Setter
public class UserProgressDto {

    // 用户ID
    private String userId;

    // 系统课程总数
    private Integer totalCourses;

    // 已复习过的课程数
    private Integer reviewedCourses;

    // 总复习次数
    private Integer totalReviews;

    // 活跃天数
    private Integer activeDays;

    // 连续复习天数
    private Integer streakDays;

    // 最近复习时间
    private LocalDateTime lastReviewTime;

    // 综合学习评分（0-100）
    private Integer overallScore;
}
