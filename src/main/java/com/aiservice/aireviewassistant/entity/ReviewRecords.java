package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 复习记录表。
 * <p>
 * 存储用户在学习过程中的提问与 AI 回答，便于后续追踪复习情况、生成错题本或学习报告。
 * </p>
 */
@Getter
@Setter
@TableName("review_records")
public class ReviewRecords {

    /** 记录主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 关联会话 ID，对应 {@link Conversation#id}。 */
    @TableField("conversation_id")
    private Integer conversationId;

    /** 关联课程 ID，对应 {@link Courses#id}；可为空。 */
    @TableField("course_id")
    private Integer courseId;

    /** 所属用户 ID，对应 {@link AppUser#id}。 */
    @TableField("user_id")
    private String userId;

    /** 用户提出的问题。 */
    @TableField("question")
    private String question;

    /** AI 生成的回答内容。 */
    @TableField("answer")
    private String answer;

    /** 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
