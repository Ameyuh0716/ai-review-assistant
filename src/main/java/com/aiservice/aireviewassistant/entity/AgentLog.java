package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

// Agent调用日志表：记录每次Agent调用的意图、耗时、成功状态等
@Getter
@Setter
@TableName("agent_log")
public class AgentLog {

    // 日志ID
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    // 关联会话ID
    @TableField("conversation_id")
    private Integer conversationId;

    // 用户消息
    @TableField("user_message")
    private String userMessage;

    // 识别到的意图
    @TableField("intent")
    private String intent;

    // 参数JSON
    @TableField("parameters")
    private String parameters;

    // 耗时（毫秒）
    @TableField("latency_ms")
    private Long latencyMs;

    // 是否成功
    @TableField("success")
    private Boolean success;

    // 错误信息
    @TableField("error_msg")
    private String errorMsg;

    // 创建时间
    @TableField("created_at")
    private LocalDateTime createdAt;
}
