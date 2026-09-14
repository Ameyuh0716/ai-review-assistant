package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.RagSearchLog;
import com.aiservice.aireviewassistant.mapper.RagSearchLogMapper;
import com.aiservice.aireviewassistant.service.RagSearchLogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

// RAG检索日志 服务实现类
@Service
public class RagSearchLogServiceImpl extends ServiceImpl<RagSearchLogMapper, RagSearchLog> implements RagSearchLogService {
}
