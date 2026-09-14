package com.aiservice.aireviewassistant.controller;

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

// 会话控制器：管理多轮对话的会话
@Tag(name = "会话管理", description = "会话 CRUD 与生命周期清理")
@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationLifecycleService lifecycleService;

    public ConversationController(ConversationService conversationService,
                                  ConversationLifecycleService lifecycleService) {
        this.conversationService = conversationService;
        this.lifecycleService = lifecycleService;
    }

    // 创建新会话（返回会话对象，含自增 ID）
    @Operation(summary = "创建新会话")
    @PostMapping
    public Conversation create(@RequestBody Conversation conversation,
                               @RequestAttribute(required = false) Integer currentUserId) {
        if (currentUserId != null) {
            conversation.setUserId(String.valueOf(currentUserId));
        }
        conversationService.save(conversation);
        return conversation;
    }

    // 查询会话列表（按当前用户过滤）
    @Operation(summary = "查询会话列表")
    @GetMapping
    public List<Conversation> list(@RequestAttribute(required = false) Integer currentUserId) {
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        wrapper.orderByDesc("updated_at");
        if (currentUserId != null) {
            wrapper.eq("user_id", String.valueOf(currentUserId));
        }
        return conversationService.list(wrapper);
    }

    // 根据ID查询会话
    @Operation(summary = "根据ID查询会话")
    @GetMapping("/{id}")
    public Conversation getById(@PathVariable Integer id) {
        return conversationService.getById(id);
    }

    // 更新会话标题
    @Operation(summary = "更新会话标题")
    @PutMapping("/{id}")
    public boolean update(@PathVariable Integer id, @RequestBody Conversation conversation) {
        conversation.setId(id);
        return conversationService.updateById(conversation);
    }

    // 删除会话（级联删除关联数据）
    @Operation(summary = "删除会话及其关联数据")
    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Integer id) {
        lifecycleService.deleteConversation(id);
        return true;
    }

    // 手动清理过期会话
    @Operation(summary = "手动清理过期会话")
    @PostMapping("/cleanup")
    public Map<String, Object> cleanup(@RequestParam(required = false, defaultValue = "30") int days) {
        int count = lifecycleService.cleanupBeforeDays(days);
        Map<String, Object> result = new HashMap<>();
        result.put("deleted", count);
        result.put("success", true);
        return result;
    }
}
