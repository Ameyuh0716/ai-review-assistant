package com.aiservice.aireviewassistant.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

// RAG检索日志实体类：记录每次向量检索的查询、召回结果与性能
@Getter
@Setter
@TableName("rag_search_log")
public class RagSearchLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Integer id;

    @TableField("conversation_id")
    private Integer conversationId;

    @TableField("query")
    private String query;

    @TableField("result_count")
    private Integer resultCount;

    @TableField("top_k")
    private Integer topK;

    @TableField("similarity_threshold")
    private Double similarityThreshold;

    @TableField("retrieved_chunks")
    private String retrievedChunks;

    @TableField("response_text")
    private String responseText;

    @TableField("success")
    private Boolean success;

    @TableField("error_msg")
    private String errorMsg;

    @TableField("latency_ms")
    private Long latencyMs;

    @TableField("created_at")
    private LocalDateTime createdAt;
}
