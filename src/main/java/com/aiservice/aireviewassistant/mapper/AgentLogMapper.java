package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.AgentLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * Agent 调用日志表 Mapper 接口。
 * <p>
 * 对应数据库表 {@code agent_log}，实体类型为 {@link AgentLog}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于记录每次 Agent 调用的输入、意图、参数、耗时及执行状态。
 * </p>
 */
public interface AgentLogMapper extends BaseMapper<AgentLog> {
}
