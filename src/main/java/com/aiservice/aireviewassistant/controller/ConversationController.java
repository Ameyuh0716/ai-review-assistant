package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.service.ConversationLifecycleService;
import com.aiservice.aireviewassistant.service.ConversationService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 会话控制器。
 * <p>负责多轮对话会话的创建、查询、更新、删除及生命周期清理，支持按当前登录用户过滤会话列表。</p>
 */
@Tag(name = "会话管理", description = "会话 CRUD 与生命周期清理")
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationLifecycleService lifecycleService;

    /**
     * 构造方法，注入会话基础服务与会话生命周期服务。
     *
     * @param conversationService 会话基础 CRUD 服务
     * @param lifecycleService    会话生命周期服务，负责级联删除与过期清理
     */
    public ConversationController(ConversationService conversationService,
                                  ConversationLifecycleService lifecycleService) {
        this.conversationService = conversationService;
        this.lifecycleService = lifecycleService;
    }

    /**
     * 创建新会话。
     * <p>HTTP: {@code POST /api/conversations}</p>
     * <p>若存在当前登录用户，则将会话关联到该用户；保存后返回包含自增 ID 的会话对象。</p>
     *
     * @param conversation  待保存的会话对象
     * @param currentUserId 可选的当前登录用户 ID，用于设置会话归属
     * @return 保存后的会话对象
     */
    @Operation(summary = "创建新会话")
    @PostMapping
    public ApiResponse<Conversation> create(@RequestBody Conversation conversation,
                               @RequestAttribute(required = false) Integer currentUserId) {
        // 权限校验与归属设置：未登录用户创建匿名会话，已登录用户关联到当前用户
        if (currentUserId != null) {
            conversation.setUserId(String.valueOf(currentUserId));
        }
        conversationService.save(conversation);
        return ApiResponse.success(conversation);
    }

    /**
     * 查询当前用户的会话列表。
     * <p>HTTP: {@code GET /api/conversations}</p>
     * <p>按更新时间倒序排列，未登录用户返回全部会话。</p>
     *
     * @param currentUserId 可选的当前登录用户 ID
     * @return 会话列表
     */
    @Operation(summary = "查询会话列表")
    @GetMapping
    public ApiResponse<List<Conversation>> list(@RequestAttribute(required = false) Integer currentUserId) {
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        // 按最后更新时间倒序，确保最新会话在前
        wrapper.orderByDesc("updated_at");
        // 已登录用户按 user_id 过滤，保证数据隔离
        if (currentUserId != null) {
            wrapper.eq("user_id", String.valueOf(currentUserId));
        }
        return ApiResponse.success(conversationService.list(wrapper));
    }

    /**
     * 根据 ID 查询单个会话。
     * <p>HTTP: {@code GET /api/conversations/{id}}</p>
     *
     * @param id 会话 ID
     * @return 会话对象
     */
    @Operation(summary = "根据ID查询会话")
    @GetMapping("/{id}")
    public ApiResponse<Conversation> getById(@PathVariable Integer id) {
        return ApiResponse.success(conversationService.getById(id));
    }

    /**
     * 更新会话信息（如标题）。
     * <p>HTTP: {@code PUT /api/conversations/{id}}</p>
     *
     * @param id           会话 ID
     * @param conversation 包含更新字段的会话对象
     * @return 是否更新成功
     */
    @Operation(summary = "更新会话标题")
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable Integer id, @RequestBody Conversation conversation) {
        conversation.setId(id);
        return ApiResponse.success(conversationService.updateById(conversation));
    }

    /**
     * 删除会话及其关联数据。
     * <p>HTTP: {@code DELETE /api/conversations/{id}}</p>
     * <p>通过生命周期服务级联删除会话下的消息等关联数据。</p>
     *
     * @param id 会话 ID
     * @return 固定返回 true，实际删除结果由生命周期服务处理
     */
    @Operation(summary = "删除会话及其关联数据")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Integer id) {
        lifecycleService.deleteConversation(id);
        return ApiResponse.success(true);
    }

    /**
     * 手动清理过期会话。
     * <p>HTTP: {@code POST /api/conversations/cleanup?days=30}</p>
     *
     * @param days 过期天数阈值，默认 30 天
     * @return 包含删除数量与成功标志的映射
     */
    @Operation(summary = "手动清理过期会话")
    @PostMapping("/cleanup")
    public ApiResponse<Map<String, Object>> cleanup(@RequestParam(required = false, defaultValue = "30") int days) {
        int count = lifecycleService.cleanupBeforeDays(days);
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", count);
        result.put("success", true);
        return ApiResponse.success(result);
    }
}
