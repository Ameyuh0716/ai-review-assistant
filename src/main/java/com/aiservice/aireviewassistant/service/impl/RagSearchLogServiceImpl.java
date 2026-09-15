package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.RagSearchLog;
import com.aiservice.aireviewassistant.mapper.RagSearchLogMapper;
import com.aiservice.aireviewassistant.service.RagSearchLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * RAG 检索日志服务实现类。
 * <p>
 * 基于 MyBatis-Plus 通用 ServiceImpl 实现基础 CRUD，无额外业务逻辑。
 * 检索日志的字段组装与调用由 {@link com.aiservice.aireviewassistant.service.impl.RagServiceImpl} 负责。
 * </p>
 */
@Service
public class RagSearchLogServiceImpl extends ServiceImpl<RagSearchLogMapper, RagSearchLog> implements RagSearchLogService {
}
