package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 课程表。
 * <p>
 * 存储用户创建的课程基本信息，包括课程名称、描述及所属用户，
 * 用于组织学习资料、复习计划与错题本。
 * </p>
 */
@Getter
@Setter
@TableName("courses")
public class Courses {

    /** 课程主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 课程所属用户 ID，对应 {@link AppUser#id}。 */
    @TableField("user_id")
    private Integer userId;

    /** 课程名称。 */
    @TableField("name")
    private String name;

    /** 课程描述，补充说明课程范围或学习目标。 */
    @TableField("description")
    private String description;

    /** 课程创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /** 课程最后更新时间。 */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
