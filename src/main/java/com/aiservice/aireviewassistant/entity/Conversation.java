package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 会话表：存储多轮对话的会话基本信息
@Getter
@Setter
@TableName("conversation")
public class Conversation {

    // 会话ID
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    // 会话标题
    @TableField("title")
    private String title;

    // 用户ID
    @TableField("user_id")
    private String userId;

    // 关联课程ID
    @TableField("course_id")
    private Integer courseId;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;

    // 更新时间
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
