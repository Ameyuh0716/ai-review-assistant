package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.RagSearchLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * RAG 检索日志表 Mapper 接口。
 * <p>
 * 对应数据库表 {@code rag_search_log}，实体类型为 {@link RagSearchLog}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于记录每次向量检索的查询文本、召回结果、相似度阈值、性能耗时及成败状态。
 * </p>
 */
@Mapper
public interface RagSearchLogMapper extends BaseMapper<RagSearchLog> {
}
