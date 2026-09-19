package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.dto.KnowledgeChunkDto;
import com.aiservice.aireviewassistant.dto.KnowledgeDocumentDto;
import com.aiservice.aireviewassistant.service.DocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库管理控制器。
 * <p>负责管理课程下的向量知识分块，包括分页查询、数量统计与批量删除。</p>
 */
@Tag(name = "知识库管理", description = "查看、统计、删除课程下的向量分块")
@RestController
@RequestMapping("/api/knowledge-base")
public class KnowledgeBaseController {

    private final DocumentService documentService;

    /**
     * 构造方法，注入文档服务。
     *
     * @param documentService 文档处理服务，同时提供知识块查询与删除能力
     */
    public KnowledgeBaseController(DocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 查询课程下的向量分块（支持分页）。
     * <p>HTTP: {@code GET /api/knowledge-base/{courseId}/chunks?page=1&pageSize=20}</p>
     * <p>为防止单页数据过大，对 pageSize 做上限保护。</p>
     *
     * @param courseId 课程 ID
     * @param page     当前页码，默认 1
     * @param pageSize 每页条数，默认 20，最大 100
     * @return 包含分块列表、总数、分页信息的映射
     */
    @Operation(summary = "查询课程下的向量分块")
    @GetMapping("/{courseId}/chunks")
    public ApiResponse<Map<String, Object>> listChunks(
            @PathVariable Integer courseId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // 分页参数保护：单页最多返回 100 条，避免前端传入过大值
        pageSize = Math.min(pageSize, 100);
        List<KnowledgeChunkDto> chunks = documentService.listChunks(courseId, page, pageSize);
        long total = documentService.countChunks(courseId);

        // 组装分页响应结果
        Map<String, Object> result = new HashMap<>();
        result.put("items", chunks);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("totalPages", (total + pageSize - 1) / pageSize);
        return ApiResponse.success(result);
    }

    /**
     * 查询课程下的资料列表（按上传文件聚合，每份资料含其全部分块）。
     * <p>HTTP: {@code GET /api/knowledge-base/{courseId}/documents}</p>
     * <p>知识库管理页默认展示资料列表，用户点击某份资料后再展开其分块。</p>
     *
     * @param courseId 课程 ID
     * @return 包含资料列表与总分块数的映射
     */
    @Operation(summary = "查询课程下的资料列表（按资料聚合分块）")
    @GetMapping("/{courseId}/documents")
    public ApiResponse<Map<String, Object>> listDocuments(@PathVariable Integer courseId) {
        List<KnowledgeDocumentDto> documents = documentService.listDocuments(courseId);
        Map<String, Object> result = new HashMap<>();
        result.put("documents", documents);
        result.put("documentCount", documents.size());
        result.put("totalChunks", documentService.countChunks(courseId));
        return ApiResponse.success(result);
    }

    /**
     * 查询某份资料的完整原文（用于“预览源文件”）。
     * <p>HTTP: {@code GET /api/knowledge-base/{courseId}/document-content?name=xxx.md}</p>
     * <p>优先返回上传时保存的原文；历史资料自动回退为分块拼接。</p>
     *
     * @param courseId 课程 ID
     * @param name     资料名称（含扩展名）
     * @return 包含资料名称与完整内容的映射；资料不存在返回 404
     */
    @Operation(summary = "查询资料完整原文")
    @GetMapping("/{courseId}/document-content")
    public ApiResponse<Map<String, Object>> documentContent(@PathVariable Integer courseId,
                                                            @RequestParam("name") String name) {
        String content = documentService.getDocumentContent(courseId, name);
        if (content == null) {
            return ApiResponse.error(404, "资料不存在或内容为空");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("name", name);
        result.put("content", content);
        result.put("length", content.length());
        return ApiResponse.success(result);
    }

    /**
     * 统计课程下的向量分块数量。
     * <p>HTTP: {@code GET /api/knowledge-base/{courseId}/count}</p>
     *
     * @param courseId 课程 ID
     * @return 包含分块数量的映射
     */
    @Operation(summary = "统计课程下的向量分块数量")
    @GetMapping("/{courseId}/count")
    public ApiResponse<Map<String, Long>> countChunks(@PathVariable Integer courseId) {
        Map<String, Long> result = new HashMap<>();
        result.put("count", documentService.countChunks(courseId));
        return ApiResponse.success(result);
    }

    /**
     * 删除课程下的所有向量分块。
     * <p>HTTP: {@code DELETE /api/knowledge-base/{courseId}}</p>
     *
     * @param courseId 课程 ID
     * @return 包含删除数量与成功标志的映射
     */
    @Operation(summary = "删除课程下的所有向量分块")
    @DeleteMapping("/{courseId}")
    public ApiResponse<Map<String, Object>> deleteChunks(@PathVariable Integer courseId) {
        int deleted = documentService.deleteChunksByCourseId(courseId);
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("success", true);
        return ApiResponse.success(result);
    }
}
