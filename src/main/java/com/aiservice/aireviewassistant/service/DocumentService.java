package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.dto.KnowledgeChunkDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public interface DocumentService {
    /**
     * 上传文档并向量化
     */
    String uploadAndVectorize(Integer courseId, MultipartFile file);

    /**
     * 直接导入文本内容并向量化
     */
    String importContent(Integer courseId, String content);

    /**
     * 查询课程下的所有向量分块
     */
    List<KnowledgeChunkDto> listChunks(Integer courseId);

    /**
     * 分页查询课程下的向量分块
     */
    List<KnowledgeChunkDto> listChunks(Integer courseId, int page, int pageSize);

    /**
     * 统计课程下的向量分块数量
     */
    long countChunks(Integer courseId);

    /**
     * 删除课程下的所有向量分块
     */
    int deleteChunksByCourseId(Integer courseId);
}
