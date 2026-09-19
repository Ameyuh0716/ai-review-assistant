package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.Message;
import com.aiservice.aireviewassistant.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 消息控制器。
 * <p>负责查询会话中的历史消息，以及外部手动补充消息。</p>
 */
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    /**
     * 构造方法，注入消息服务。
     *
     * @param messageService 消息 CRUD 服务
     */
    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * 根据会话 ID 查询消息历史。
     * <p>HTTP: {@code GET /api/messages?conversationId=1}</p>
     * <p>按创建时间升序返回，保证对话时间线正确。</p>
     *
     * @param conversationId 会话 ID
     * @return 该会话下的消息列表
     */
    @GetMapping
    public ApiResponse<List<Message>> listByConversation(@RequestParam Integer conversationId) {
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", conversationId);
        wrapper.orderByAsc("created_at");
        return ApiResponse.success(messageService.list(wrapper));
    }

    /**
     * 新增一条消息。
     * <p>HTTP: {@code POST /api/messages}</p>
     * <p>通常由 Agent 内部调用，也可由外部手动补充历史消息。</p>
     *
     * @param message 待保存的消息对象
     * @return 是否保存成功
     */
    @PostMapping
    public ApiResponse<Boolean> save(@RequestBody Message message) {
        return ApiResponse.success(messageService.save(message));
    }

    /**
     * 截断会话消息：删除指定消息及其之后的所有消息。
     * <p>HTTP: {@code DELETE /api/messages/{id}/truncate}</p>
     * <p>用于对话的“编辑并重新发送”与“重新生成”功能：先移除目标位置及其后的消息，
     * 再由前端重新发起流式对话。消息不存在时返回 false（幂等）。</p>
     *
     * @param id 起始消息 ID（包含该条）
     * @return 是否有消息被删除
     */
    @DeleteMapping("/{id}/truncate")
    public ApiResponse<Boolean> truncate(@PathVariable Integer id) {
        Message message = messageService.getById(id);
        if (message == null) {
            return ApiResponse.success(false);
        }
        // 同一会话内删除 id >= 起始消息的全部消息，保持多个会话互不影响
        QueryWrapper<Message> wrapper = new QueryWrapper<>();
        wrapper.eq("conversation_id", message.getConversationId());
        wrapper.ge("id", id);
        return ApiResponse.success(messageService.remove(wrapper));
    }
}
