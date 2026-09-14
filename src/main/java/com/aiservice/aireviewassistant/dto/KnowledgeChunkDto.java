package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

// 知识库分块信息DTO
@Getter
@Setter
public class KnowledgeChunkDto {

    // 向量记录ID
    private UUID id;

    // 文本内容
    private String content;

    // 元数据JSON字符串
    private String metadata;

    // 所属课程ID
    private Integer courseId;

    // 分块索引
    private Integer chunkIndex;

    // 分块总数
    private Integer chunkTotal;
}
