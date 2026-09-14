package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 复习记录表：存储用户的提问和AI的回答，方便追踪复习情况
@Getter
@Setter
@TableName("review_records")
public class ReviewRecords {

    // 记录ID
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    // 关联会话ID
    @TableField("conversation_id")
    private Integer conversationId;

    // 关联课程ID
    @TableField("course_id")
    private Integer courseId;

    // 用户ID
    @TableField("user_id")
    private String userId;

    // 用户问题
    @TableField("question")
    private String question;

    // AI回答
    @TableField("answer")
    private String answer;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;
}
