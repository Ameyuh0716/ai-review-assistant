package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * Agent 调用日志表。
 * <p>
 * 记录每次 Agent 调用的输入、识别到的意图、调用参数、执行耗时、成功状态及错误信息，
 * 用于问题排查、性能分析与调用链路审计。
 * </p>
 */
@Getter
@Setter
@TableName("agent_log")
public class AgentLog {

    /** 日志主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 关联会话 ID，对应 {@link Conversation#id}。 */
    @TableField("conversation_id")
    private Integer conversationId;

    /** 用户输入的原始消息内容。 */
    @TableField("user_message")
    private String userMessage;

    /** 识别到的用户意图，如 chat、quiz、plan、rag 等。 */
    @TableField("intent")
    private String intent;

    /** 调用参数，以 JSON 字符串形式存储，用于记录工具入参。 */
    @TableField("parameters")
    private String parameters;

    /** 本次调用耗时，单位：毫秒。 */
    @TableField("latency_ms")
    private Long latencyMs;

    /** 调用是否成功：true 成功，false 失败。 */
    @TableField("success")
    private Boolean success;

    /** 失败时的错误信息；成功时一般为空。 */
    @TableField("error_msg")
    private String errorMsg;

    /** 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
