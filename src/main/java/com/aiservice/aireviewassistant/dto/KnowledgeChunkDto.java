package com.aiservice.aireviewassistant.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * 知识库分块信息 DTO。
 * <p>用于在 RAG 检索结果、管理后台中展示向量数据库中的文档分片，包含原始文本、
 * 元数据以及分块位置信息。</p>
 */
@Getter
@Setter
public class KnowledgeChunkDto {
    /** 向量记录唯一标识（向量数据库中的主键）。 */
    private UUID id;

    /** 该分块的文本内容。 */
    private String content;

    /** 元数据 JSON 字符串，包含文件名、原始段落等扩展信息；使用时需反序列化。 */
    private String metadata;

    /** 所属课程 ID。 */
    private Integer courseId;

    /** 当前分块所属的原始文件/资料名称；历史数据可能为空。 */
    private String fileName;

    /** 当前分块在原文档中的序号，从 0 开始。 */
    private Integer chunkIndex;

    /** 原文档被切分后的总分块数。 */
    private Integer chunkTotal;
}
