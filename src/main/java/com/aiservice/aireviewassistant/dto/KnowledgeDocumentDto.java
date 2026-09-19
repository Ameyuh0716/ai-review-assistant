package com.aiservice.aireviewassistant.dto;

import java.util.List;

/**
 * 知识库中的一份资料（按上传文件聚合）。
 * <p>
 * 用于知识库管理页“默认展示资料、按需展开分块”的结构：
 * 前端先列出课程下的全部资料（文件名 + 分块数），用户点击某份资料后再展示其分块明细。
 * </p>
 *
 * @param id              原始资料记录 ID；历史数据（无原文存储）为 null
 * @param name            资料名称（上传文件名；历史数据无文件名时使用兜底名称）
 * @param chunkCount      该资料的分块数量
 * @param hasFullContent  是否存在完整原文（用于前端决定是否展示“预览原文”入口）
 * @param chunks          该资料的全部分块（按分块序号升序）
 */
public record KnowledgeDocumentDto(Integer id, String name, int chunkCount,
                                   boolean hasFullContent, List<KnowledgeChunkDto> chunks) {
}
