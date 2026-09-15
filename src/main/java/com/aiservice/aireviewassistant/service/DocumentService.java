package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.dto.KnowledgeChunkDto;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库文档服务接口。
 * <p>
 * 负责课程相关文档的上传、解析、分块、向量化存储，
 * 以及向量分块的查询、统计与删除。支持的文档格式包括 txt、md、pdf、doc、docx。
 * </p>
 */
@Service
public interface DocumentService {
    /**
     * 上传文档并解析、分块、向量化后存入向量库。
     *
     * @param courseId 课程 ID
     * @param file     上传的文件
     * @return 处理结果描述，包含成功/失败信息及分块数量
     */
    String uploadAndVectorize(Integer courseId, MultipartFile file);

    /**
     * 直接导入文本内容，分块、向量化后存入向量库。
     *
     * @param courseId 课程 ID
     * @param content  原始文本内容
     * @return 处理结果描述，包含成功/失败信息及分块数量
     */
    String importContent(Integer courseId, String content);

    /**
     * 查询课程下的全部向量分块，按分块索引升序排列。
     *
     * @param courseId 课程 ID
     * @return 课程对应的知识分块列表
     */
    List<KnowledgeChunkDto> listChunks(Integer courseId);

    /**
     * 分页查询课程下的向量分块，按分块索引升序排列。
     *
     * @param courseId 课程 ID
     * @param page     页码，从 1 开始
     * @param pageSize 每页大小
     * @return 指定页的知识分块列表
     */
    List<KnowledgeChunkDto> listChunks(Integer courseId, int page, int pageSize);

    /**
     * 统计课程下的向量分块数量。
     *
     * @param courseId 课程 ID
     * @return 向量分块总数
     */
    long countChunks(Integer courseId);

    /**
     * 删除课程下的所有向量分块。
     *
     * @param courseId 课程 ID
     * @return 受影响的行数
     */
    int deleteChunksByCourseId(Integer courseId);
}
