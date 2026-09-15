package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 单课程学习进度统计 DTO。
 * <p>用于展示某个用户在指定课程上的复习情况，包括复习频次、活跃天数、连续天数以及掌握度评分。</p>
 */
@Getter
@Setter
public class StudyProgressDto {

    /** 课程 ID。 */
    private Integer courseId;

    /** 课程名称。 */
    private String courseName;

    /** 在该课程上的总复习次数。 */
    private Integer totalReviews;

    /** 复习覆盖的不同日期数（去重后的活跃天数）。 */
    private Integer activeDays;

    /** 连续复习天数。 */
    private Integer streakDays;

    /** 最近一次复习时间。 */
    private LocalDateTime lastReviewTime;

    /**
     * 掌握度评分，取值范围 0-100。
     * <p>基于复习频次和连续性估算得出，分数越高表示掌握程度越好。</p>
     */
    private Integer masteryScore;
}
