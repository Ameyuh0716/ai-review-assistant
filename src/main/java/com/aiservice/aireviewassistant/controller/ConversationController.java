package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.security.ConversationAccessGuard;
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
 * <p>负责多轮对话会话的创建、查询、更新、删除及生命周期清理。
 * <b>所有接口都以认证身份为唯一归属依据，并逐一校验会话归属</b>，防止越权读写他人会话。</p>
 */
@Tag(name = "会话管理", description = "会话 CRUD 与生命周期清理")
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationLifecycleService lifecycleService;

    /** 会话归属守卫，统一拦截越权访问。 */
    private final ConversationAccessGuard accessGuard;

    /**
     * 构造方法，注入会话基础服务、生命周期服务与归属守卫。
     *
     * @param conversationService 会话基础 CRUD 服务
     * @param lifecycleService    会话生命周期服务，负责级联删除与过期清理
     * @param accessGuard         会话归属守卫，用于对象级授权校验
     */
    public ConversationController(ConversationService conversationService,
                                  ConversationLifecycleService lifecycleService,
                                  ConversationAccessGuard accessGuard) {
        this.conversationService = conversationService;
        this.lifecycleService = lifecycleService;
        this.accessGuard = accessGuard;
    }

    /**
     * 创建新会话。
     * <p>HTTP: {@code POST /api/conversations}</p>
     * <p>会话归属<b>只由认证身份决定</b>：请求体中携带的 userId 一律被忽略，
     * 避免通过构造请求把会话直接挂到他人名下。未登录时拒绝创建。</p>
     *
     * @param conversation  待保存的会话对象
     * @param currentUserId 当前登录用户 ID，未登录时为 null
     * @return 保存后的会话对象；未登录时返回 401
     */
    @Operation(summary = "创建新会话")
    @PostMapping
    public ApiResponse<Conversation> create(@RequestBody Conversation conversation,
                               @RequestAttribute(required = false) Integer currentUserId) {
        // 会话是用户私有数据，未认证不允许创建（否则会产生无法归属的孤儿会话）
        if (currentUserId == null) {
            return ApiResponse.error(401, "请先登录后再创建会话");
        }
        // 归属只信任认证信息，不信任请求体
        conversation.setUserId(String.valueOf(currentUserId));
        conversationService.save(conversation);
        return ApiResponse.success(conversation);
    }

    /**
     * 查询当前用户的会话列表。
     * <p>HTTP: {@code GET /api/conversations}</p>
     * <p>按更新时间倒序排列，<b>仅返回当前登录用户自己的会话</b>。</p>
     * <p>
     * <b>修复说明：</b>历史实现在 {@code currentUserId} 为 null 时<b>不加任何过滤条件</b>，
     * 导致匿名请求可以直接拿到全库所有用户的会话列表（含标题，即用户提问的前 30 字）。
     * 现在未认证直接拒绝，且过滤条件无条件生效。
     * </p>
     *
     * @param currentUserId 当前登录用户 ID，未登录时为 null
     * @return 当前用户的会话列表；未登录时返回 401
     */
    @Operation(summary = "查询会话列表")
    @GetMapping
    public ApiResponse<List<Conversation>> list(@RequestAttribute(required = false) Integer currentUserId) {
        // 未认证直接拒绝：绝不能因为"没有用户身份"就退化为"返回全部数据"
        if (currentUserId == null) {
            return ApiResponse.error(401, "请先登录后再查看会话列表");
        }
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        // 数据隔离：过滤条件无条件生效
        wrapper.eq("user_id", String.valueOf(currentUserId));
        // 按最后更新时间倒序，确保最新会话在前
        wrapper.orderByDesc("updated_at");
        return ApiResponse.success(conversationService.list(wrapper));
    }

    /**
     * 根据 ID 查询单个会话。
     * <p>HTTP: {@code GET /api/conversations/{id}}</p>
     * <p>不属于当前用户的会话一律返回 403（与"不存在"表现一致，避免探测他人会话是否存在）。</p>
     *
     * @param id            会话 ID
     * @param currentUserId 当前登录用户 ID
     * @return 会话对象；越权或不存在时返回 403
     */
    @Operation(summary = "根据ID查询会话")
    @GetMapping("/{id}")
    public ApiResponse<Conversation> getById(@PathVariable Integer id,
                                             @RequestAttribute(required = false) Integer currentUserId) {
        if (!accessGuard.canAccessConversation(id, currentUserId)) {
            return ApiResponse.error(403, "无权访问该会话");
        }
        return ApiResponse.success(conversationService.getById(id));
    }

    /**
     * 更新会话信息（如标题）。
     * <p>HTTP: {@code PUT /api/conversations/{id}}</p>
     * <p>仅允许修改自己名下会话，且归属字段不可通过请求体改写。</p>
     *
     * @param id            会话 ID
     * @param conversation  包含更新字段的会话对象
     * @param currentUserId 当前登录用户 ID
     * @return 是否更新成功；越权时返回 403
     */
    @Operation(summary = "更新会话标题")
    @PutMapping("/{id}")
    public ApiResponse<Boolean> update(@PathVariable Integer id, @RequestBody Conversation conversation,
                                       @RequestAttribute(required = false) Integer currentUserId) {
        if (!accessGuard.canAccessConversation(id, currentUserId)) {
            return ApiResponse.error(403, "无权修改该会话");
        }
        conversation.setId(id);
        // 置空归属字段：MyBatis-Plus 默认策略会忽略 null 字段，
        // 从而防止"改标题"的同时把会话转移给他人（或伪造 userId/courseId）
        conversation.setUserId(null);
        return ApiResponse.success(conversationService.updateById(conversation));
    }

    /**
     * 删除会话及其关联数据。
     * <p>HTTP: {@code DELETE /api/conversations/{id}}</p>
     * <p>通过生命周期服务级联删除会话下的消息等关联数据；仅允许删除自己名下会话。</p>
     *
     * @param id            会话 ID
     * @param currentUserId 当前登录用户 ID
     * @return 是否删除成功；越权时返回 403
     */
    @Operation(summary = "删除会话及其关联数据")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Integer id,
                                       @RequestAttribute(required = false) Integer currentUserId) {
        if (!accessGuard.canAccessConversation(id, currentUserId)) {
            return ApiResponse.error(403, "无权删除该会话");
        }
        lifecycleService.deleteConversation(id);
        return ApiResponse.success(true);
    }

    /**
     * 手动清理过期会话（运维操作）。
     * <p>HTTP: {@code POST /api/conversations/cleanup?days=30}</p>
     * <p>会跨用户删除长期不活跃会话，因此由 {@code SecurityConfig} 限制为 ADMIN 角色可调用。</p>
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
