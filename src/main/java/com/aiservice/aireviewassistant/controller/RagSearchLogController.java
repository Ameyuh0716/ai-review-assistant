package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.entity.RagSearchLog;
import com.aiservice.aireviewassistant.service.RagSearchLogService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// RAG检索日志控制器：查询向量检索日志
@Tag(name = "RAG 检索日志", description = "查询向量检索的召回结果与质量")
@RestController
@RequestMapping("/api/rag-search-logs")
public class RagSearchLogController {

    private final RagSearchLogService ragSearchLogService;

    public RagSearchLogController(RagSearchLogService ragSearchLogService) {
        this.ragSearchLogService = ragSearchLogService;
    }

    // 查询所有RAG检索日志（支持按 conversationId 过滤）
    @Operation(summary = "查询 RAG 检索日志")
    @GetMapping
    public List<RagSearchLog> list(@RequestParam(required = false) Integer conversationId) {
        QueryWrapper<RagSearchLog> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("created_at");
        if (conversationId != null) {
            wrapper.eq("conversation_id", conversationId);
        }
        return ragSearchLogService.list(wrapper);
    }

    // 根据ID查询日志
    @Operation(summary = "根据ID查询 RAG 检索日志")
    @GetMapping("/{id}")
    public RagSearchLog getById(@PathVariable Integer id) {
        return ragSearchLogService.getById(id);
    }
}
