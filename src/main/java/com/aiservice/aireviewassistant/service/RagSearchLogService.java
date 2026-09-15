package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.RagSearchLog;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * RAG 检索日志服务接口。
 * <p>
 * 继承 MyBatis-Plus 通用 CRUD 能力，用于 {@link com.aiservice.aireviewassistant.entity.RagSearchLog}
 * 实体的持久化与查询。具体业务写入逻辑由 {@link com.aiservice.aireviewassistant.service.RagService}
 * 在检索完成后调用。
 * </p>
 */
public interface RagSearchLogService extends IService<RagSearchLog> {
}
