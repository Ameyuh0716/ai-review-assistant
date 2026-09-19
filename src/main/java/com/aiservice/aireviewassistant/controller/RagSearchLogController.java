package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.RagSearchLog;
import com.aiservice.aireviewassistant.service.RagSearchLogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * RAG 检索日志控制器。
 * <p>提供向量检索召回结果与质量的查询接口，基础路径为 /api/rag-search-logs。</p>
 */
@Tag(name = "RAG 检索日志", description = "查询向量检索的召回结果与质量")
@RestController
@RequestMapping("/api/rag-search-logs")
public class RagSearchLogController {

    private final RagSearchLogService ragSearchLogService;

    /**
     * 通过依赖注入构造控制器。
     *
     * @param ragSearchLogService RAG 检索日志服务
     */
    public RagSearchLogController(RagSearchLogService ragSearchLogService) {
        this.ragSearchLogService = ragSearchLogService;
    }

    /**
     * 查询 RAG 检索日志列表。
     * <p>GET /api/rag-search-logs，支持按 conversationId 过滤，结果按创建时间倒序排列。</p>
     *
     * @param conversationId 会话 ID（可选）
     * @return RAG 检索日志列表
     */
    @Operation(summary = "查询 RAG 检索日志")
    @GetMapping
    public ApiResponse<List<RagSearchLog>> list(@RequestParam(required = false) Integer conversationId) {
        QueryWrapper<RagSearchLog> wrapper = new QueryWrapper<>();
        // 按创建时间倒序排列，最新日志在前
        wrapper.orderByDesc("created_at");
        // 如果指定了会话 ID，则按会话过滤
        if (conversationId != null) {
            wrapper.eq("conversation_id", conversationId);
        }
        // 限制返回数量，避免会话历史过长时响应体膨胀
        wrapper.last("LIMIT 200");
        return ApiResponse.success(ragSearchLogService.list(wrapper));
    }

    /**
     * 根据 ID 查询 RAG 检索日志。
     * <p>GET /api/rag-search-logs/{id}</p>
     *
     * @param id 日志主键
     * @return RAG 检索日志实体
     */
    @Operation(summary = "根据ID查询 RAG 检索日志")
    @GetMapping("/{id}")
    public ApiResponse<RagSearchLog> getById(@PathVariable Integer id) {
        return ApiResponse.success(ragSearchLogService.getById(id));
    }
}
