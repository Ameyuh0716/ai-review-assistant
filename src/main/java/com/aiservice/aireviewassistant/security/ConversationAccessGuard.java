package com.aiservice.aireviewassistant.security;

import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.service.ConversationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 会话资源访问守卫。
 * <p>
 * <b>为什么需要它：</b>会话与消息接口（列表、详情、更新、删除、消息历史、消息编辑/截断）
 * 都以 {@code conversationId} / {@code messageId} 作为唯一定位参数。如果不校验归属，
 * 任何请求只要猜到或遍历 ID，就能读取、篡改甚至删除<b>别人会话</b>的完整对话内容。
 * </p>
 * <p>
 * <b>与 {@link CourseAccessGuard} 的关系：</b>两者解决同一类问题（对象级授权，IDOR），
 * 只是作用对象不同——前者守课程及其知识库，后者守会话及其消息。
 * 二者与检索侧的 {@code RagService.RagScope} 共同构成"读取、检索、写入"三条边界。
 * </p>
 * <p>
 * <b>为什么不用 HTTP 403 而返回业务错误码：</b>与本项目既有约定保持一致
 * （见 {@link CourseAccessGuard} 的使用方式），统一由 {@code ApiResponse.code} 承载，
 * 前端 axios 拦截器对 {@code code != 0} 已有统一处理。
 * </p>
 */
@Slf4j
@Component
public class ConversationAccessGuard {

    private final ConversationService conversationService;

    /**
     * 构造方法。
     *
     * @param conversationService 会话服务，用于查询会话归属
     */
    public ConversationAccessGuard(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /**
     * 判断目标会话是否可被当前用户访问。
     * <p>
     * 以下情况一律视为不可访问：会话 ID 为空、用户未登录、会话不存在、会话属于他人。
     * 对"不存在"与"不属于自己"给出同样的否定结论，避免通过响应差异探测他人会话是否存在。
     * </p>
     * <p>
     * 注意：{@code conversation.userId} 在库中是<b>字符串</b>类型（历史实现为兼容匿名会话
     * 使用 {@code "anonymous"} 占位），因此比较时必须把当前用户 ID 也转成字符串。
     * </p>
     *
     * @param conversationId 目标会话 ID
     * @param userId         当前登录用户 ID，为 null 表示匿名
     * @return 允许访问时返回 true
     */
    public boolean canAccessConversation(Integer conversationId, Integer userId) {
        if (conversationId == null || userId == null) {
            return false;
        }
        try {
            Conversation conversation = conversationService.getById(conversationId);
            if (conversation == null) {
                log.debug("[Guard] 会话不存在: conversationId={}", conversationId);
                return false;
            }
            boolean owner = String.valueOf(userId).equals(conversation.getUserId());
            if (!owner) {
                log.warn("[Guard] 会话越权访问被拒绝: conversationId={} owner={} requester={}",
                    conversationId, conversation.getUserId(), userId);
            }
            return owner;
        } catch (Exception e) {
            // 校验过程本身出错时按"拒绝"处理，避免因异常放行
            log.warn("[Guard] 会话归属校验异常: conversationId={}, {}", conversationId, e.getMessage());
            return false;
        }
    }
}
