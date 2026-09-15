package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 消息表。
 * <p>
 * 存储多轮对话中的每条消息，包括用户消息、助手回复及系统消息，
 * 通过 conversation_id 与会话表关联。
 * </p>
 */
@Getter
@Setter
@TableName("message")
public class Message {

    /** 消息主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 所属会话 ID，对应 {@link Conversation#id}。 */
    @TableField("conversation_id")
    private Integer conversationId;

    /** 消息角色：user（用户）、assistant（助手）或 system（系统）。 */
    @TableField("role")
    private String role;

    /** 消息文本内容。 */
    @TableField("content")
    private String content;

    /** 识别到的用户意图，用于后续工具路由与统计。 */
    @TableField("intent")
    private String intent;

    /** 消息创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
