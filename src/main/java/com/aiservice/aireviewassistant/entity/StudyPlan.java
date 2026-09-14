package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 学习计划表：存储从对话中生成的复习计划
@Getter
@Setter
@TableName("study_plan")
public class StudyPlan {

    // 计划ID
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    // 用户ID
    @TableField("user_id")
    private Integer userId;

    // 课程名称
    @TableField("course_name")
    private String courseName;

    // 可用天数
    @TableField("available_days")
    private String availableDays;

    // 计划内容（Markdown）
    @TableField("content")
    private String content;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
