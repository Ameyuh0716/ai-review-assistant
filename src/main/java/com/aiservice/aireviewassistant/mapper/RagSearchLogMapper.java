package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.RagSearchLog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

// RAG检索日志 数据访问层
@Mapper
public interface RagSearchLogMapper extends BaseMapper<RagSearchLog> {
}
