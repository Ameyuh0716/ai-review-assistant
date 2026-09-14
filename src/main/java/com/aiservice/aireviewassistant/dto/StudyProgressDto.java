package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// 某用户某课程的学习进度统计
@Getter
@Setter
public class StudyProgressDto {

    // 课程ID
    private Integer courseId;

    // 课程名称
    private String courseName;

    // 总复习次数
    private Integer totalReviews;

    // 复习覆盖的不同日期数
    private Integer activeDays;

    // 连续复习天数
    private Integer streakDays;

    // 最近复习时间
    private LocalDateTime lastReviewTime;

    // 掌握度评分（0-100，基于复习频次和连续性估算）
    private Integer masteryScore;
}
