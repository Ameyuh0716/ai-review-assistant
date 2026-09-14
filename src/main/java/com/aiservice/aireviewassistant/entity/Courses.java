package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 课程表：存储课程基本信息，如课程名称、描述等
@Getter
@Setter
@TableName("courses")
public class Courses {

    // 课程ID
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    // 关联用户ID
    @TableField("user_id")
    private Integer userId;

    // 课程名称
    @TableField("name")
    private String name;

    // 课程描述
    @TableField("description")
    private String description;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
