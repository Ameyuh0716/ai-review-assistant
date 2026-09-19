package com.aiservice.aireviewassistant.mapper;

import com.aiservice.aireviewassistant.entity.KnowledgeDocument;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识库原始资料 Mapper 接口。
 * <p>
 * 对应数据库表 {@code knowledge_document}，实体类型为 {@link KnowledgeDocument}。
 * 继承 MyBatis-Plus 基础 CRUD 能力，用于保存与读取上传资料的完整原文。
 * </p>
 */
@Mapper
public interface KnowledgeDocumentMapper extends BaseMapper<KnowledgeDocument> {
}
