package com.aiservice.aireviewassistant.controller;

import com.aiservice.aireviewassistant.common.ApiResponse;
import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.entity.Message;
import com.aiservice.aireviewassistant.service.ConversationService;
import com.aiservice.aireviewassistant.service.MessageService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
/**
 * 消息控制器。
 * <p>负责查询会话中的历史消息，以及外部手动补充消息。</p>
 */
@Slf4j
@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;
    private final ConversationService conversationService;

    /**
     * 构造方法，注入消息服务与会话服务。
     *
     * @param messageService      消息 CRUD 服务
     * @param conversationService 会话 CRUD 服务（用于编辑首条消息时同步标题）
     */
    public MessageController(MessageService messageService, ConversationService conversationService) {
        this.messageService = messageService;
        this.conversationService = conversationService;
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
     * 更新指定消息的内容（“编辑并重新发送”场景）。
     * <p>HTTP: {@code PUT /api/messages/{id}}</p>
     * <p>仅修改该条消息的文本，<b>不删除任何历史记录</b>：编辑后的上下文由前端在重新生成时
     * 通过 {@code historyBeforeId} 截断，AI 回复则就地覆盖原行，整段对话记录完整保留。</p>
     *
     * @param id   消息 ID
     * @param body 请求体，需包含 {@code content} 字段
     * @return 更新后的消息；消息不存在或内容为空时返回 code 非 0
     */
    @PutMapping("/{id}")
    public ApiResponse<Message> update(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        Message message = messageService.getById(id);
        if (message == null) {
            return new ApiResponse<>(1, "消息不存在", null);
        }
        String content = body != null ? body.get("content") : null;
        if (content == null || content.trim().isEmpty()) {
            return new ApiResponse<>(1, "内容不能为空", null);
        }
        String oldContent = message.getContent();
        message.setContent(content.trim());
        messageService.updateById(message);
        // 编辑首条消息时，同步刷新会话标题，避免侧栏仍显示旧提问
        syncConversationTitle(message, oldContent);
        return ApiResponse.success(message);
    }

    /**
     * 编辑消息后同步会话标题。
     * <p>
     * <b>判定方式：用内容比对代替“取首条消息”。</b>
     * 会话标题是由首条用户消息生成的（见 {@code ReviewAssistantAgent#getOrCreateConversation}），
     * 因此「标题 == 本条消息编辑前的标题化结果」就等价于「本条正是标题的来源消息」，
     * 也就等价于「本条是首条消息」——不需要再查一次数据库。
     * </p>
     * <p>
     * 这样改写的原因：原先用 {@code ORDER BY id LIMIT 1} 查首条消息，
     * 优化器会按 conversation_id 的选择性（约 3.7%）估算“沿主键扫几十行即可命中”，
     * 因而放弃 (conversation_id, id) 索引改走主键索引；但同一会话的消息在物理上连续，
     * 实际要扫完整张表才找到（56 万行时实测 35～190ms，且随消息总量线性增长）。
     * 改为内容比对后该查询被彻底移除，开销为零、结果确定，也不再受优化器估算影响。
     * </p>
     *
     * @param message    已更新的消息
     * @param oldContent 更新前的消息内容
     */
    private void syncConversationTitle(Message message, String oldContent) {
        try {
            if (message.getConversationId() == null) {
                return;
            }
            Conversation conversation = conversationService.getById(message.getConversationId());
            if (conversation == null) {
                return;
            }
            String title = conversation.getTitle();
            boolean placeholder = title == null || title.isEmpty() || "新对话".equals(title);
            boolean titleFromThisMessage = oldContent != null && title != null
                && title.equals(buildTitle(oldContent));
            if (!placeholder && !titleFromThisMessage) {
                // 标题已由其它消息生成（或用户自定义），编辑本条不应改动标题
                return;
            }
            conversation.setTitle(buildTitle(message.getContent()));
            conversationService.updateById(conversation);
        } catch (Exception e) {
            log.warn("[Message] 同步会话标题失败: {}", e.getMessage());
        }
    }

    /**
     * 按首条消息生成会话标题（超长截断）。
     *
     * @param content 消息内容
     * @return 标题文本
     */
    private String buildTitle(String content) {
        if (content == null) {
            return "新对话";
        }
        return content.length() > 30 ? content.substring(0, 30) + "..." : content;
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
