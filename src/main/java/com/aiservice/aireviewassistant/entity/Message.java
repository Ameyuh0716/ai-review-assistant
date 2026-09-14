package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// 消息表：存储多轮对话中的每条消息
@Getter
@Setter
@TableName("message")
public class Message {

    // 消息ID
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    // 所属会话ID
    @TableField("conversation_id")
    private Integer conversationId;

    // 消息角色：user / assistant / system
    @TableField("role")
    private String role;

    // 消息内容
    @TableField("content")
    private String content;

    // 识别到的意图
    @TableField("intent")
    private String intent;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;
}
