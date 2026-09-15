package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 用户整体学习进度总览 DTO。
 * <p>用于在统计页面展示用户的全局学习概况，包括课程覆盖、复习频次、活跃情况以及综合评分。</p>
 */
@Getter
@Setter
public class UserProgressDto {

    /** 用户 ID。 */
    private String userId;

    /** 系统中课程总数。 */
    private Integer totalCourses;

    /** 该用户已复习过的课程数。 */
    private Integer reviewedCourses;

    /** 该用户的总复习次数（跨所有课程）。 */
    private Integer totalReviews;

    /** 该用户的活跃天数（有复习记录的日期数）。 */
    private Integer activeDays;

    /** 该用户的连续复习天数。 */
    private Integer streakDays;

    /** 该用户最近一次复习时间。 */
    private LocalDateTime lastReviewTime;

    /** 综合学习评分，取值范围 0-100。 */
    private Integer overallScore;
}
