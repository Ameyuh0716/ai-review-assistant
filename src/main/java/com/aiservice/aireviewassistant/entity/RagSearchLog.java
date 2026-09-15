package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * RAG 检索日志表。
 * <p>
 * 记录每次向量检索的查询文本、召回结果、相似度阈值、性能耗时及成败状态，
 * 用于检索效果评估与问题定位。
 * </p>
 */
@Getter
@Setter
@TableName("rag_search_log")
public class RagSearchLog {

    /** 日志主键，自增。 */
    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    /** 关联会话 ID，对应 {@link Conversation#id}。 */
    @TableField("conversation_id")
    private Integer conversationId;

    /** 用户检索 query 文本。 */
    @TableField("query")
    private String query;

    /** 实际召回的文档片段数量。 */
    @TableField("result_count")
    private Integer resultCount;

    /** 请求召回的最大片段数（Top-K）。 */
    @TableField("top_k")
    private Integer topK;

    /** 相似度阈值，仅高于该阈值的片段才会被召回。 */
    @TableField("similarity_threshold")
    private Double similarityThreshold;

    /** 召回的文档片段内容，以 JSON 字符串形式存储。 */
    @TableField("retrieved_chunks")
    private String retrievedChunks;

    /** 基于检索结果生成的最终回复文本。 */
    @TableField("response_text")
    private String responseText;

    /** 检索是否成功：true 成功，false 失败。 */
    @TableField("success")
    private Boolean success;

    /** 失败时的错误信息。 */
    @TableField("error_msg")
    private String errorMsg;

    /** 本次检索耗时，单位：毫秒。 */
    @TableField("latency_ms")
    private Long latencyMs;

    /** 记录创建时间。 */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
