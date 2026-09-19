package com.aiservice.aireviewassistant.entity;

import com.aiservice.aireviewassistant.dto.PlanDayDto;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/**
 * 学习计划表。
 * <p>
 * 存储从对话中生成的学习计划，包括目标课程、可用天数及 Markdown 格式的计划内容，
 * 用于后续复习跟踪。
 * </p>
 */
@Getter
@Setter
@TableName("study_plan")
public class StudyPlan {

    /** 计划主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 所属用户 ID，对应 {@link AppUser#id}。 */
    @TableField("user_id")
    private Integer userId;

    /** 课程名称，标识该计划对应的学习课程。 */
    @TableField("course_name")
    private String courseName;

    /** 可用学习天数，通常由用户在对话中提供。 */
    @TableField("available_days")
    private String availableDays;

    /** 计划内容，以 Markdown 格式存储，包含阶段任务与复习安排。 */
    @TableField("content")
    private String content;

    /**
     * 结构化进度（JSON 字符串）。
     * <p>记录每天各环节（复习内容/掌握内容/练习）的勾选状态与 AI 生成内容，形如：
     * {@code {"1": {"done": true, "review": {"done": true, "content": "..."}}}}。</p>
     */
    @TableField("progress")
    private String progress;

    /**
     * 从正文解析出的每日结构（非数据库字段）。
     * <p>由服务层在查询后填充，供前端渲染结构化视图；无分天结构时为空列表。</p>
     */
    @TableField(exist = false)
    private List<PlanDayDto> days;

    /** 计划创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 计划最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
