package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.config.AppProperties;
import com.aiservice.aireviewassistant.entity.Conversation;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 会话生命周期管理服务。
 * <p>负责会话的过期清理、手动清理与级联删除。
 * 通过定时任务与手动入口两种形式，按更新时间阈值删除会话及其关联表数据，
 * 避免历史会话无限增长。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationLifecycleService {

    private final ConversationService conversationService;
    private final AppProperties appProperties;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 自动清理过期会话。
     * <p>由 Spring 定时任务调度，每天凌晨 3 点执行一次。
     * 根据配置的 {@code appProperties.conversation.expireDays} 计算过期时间点，
     * 删除该时间之前未更新的会话及其关联数据。整个方法在事务边界内执行，
     * 发生异常时全部回滚。</p>
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredConversations() {
        // 从配置中读取会话过期天数
        int expireDays = appProperties.getConversation().getExpireDays();
        // 计算过期时间点：当前时间往前推 expireDays 天
        LocalDateTime expireTime = LocalDateTime.now().minusDays(expireDays);

        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        // 查询 updated_at 早于过期时间的会话
        wrapper.lt("updated_at", expireTime);
        List<Conversation> expired = conversationService.list(wrapper);

        // 复杂条件分支：无过期会话时直接记录日志并返回，避免无意义删除
        if (expired.isEmpty()) {
            log.info("[Lifecycle] 无过期会话需要清理");
            return;
        }

        // 逐个级联删除会话关联数据（受外层事务保护）
        for (Conversation conversation : expired) {
            deleteConversationData(conversation.getId());
        }
        log.info("[Lifecycle] 已清理 {} 个过期会话", expired.size());
    }

    /**
     * 手动清理指定天数前的会话。
     * <p>供管理入口或手动触发场景使用，按传入天数计算过期时间并级联删除。
     * 事务边界：所有删除在同一事务中完成，异常时回滚。</p>
     *
     * @param days 过期天数阈值，需为正数；早于该天数的会话将被清理
     * @return 实际清理的会话数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int cleanupBeforeDays(int days) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(days);
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        wrapper.lt("updated_at", expireTime);
        List<Conversation> expired = conversationService.list(wrapper);

        // 权限校验/业务边界：此处未做用户级隔离，调用方应确保有权限执行批量清理
        for (Conversation conversation : expired) {
            deleteConversationData(conversation.getId());
        }
        return expired.size();
    }

    /**
     * 删除单条会话及其关联数据。
     * <p>提供手动删除指定会话的能力，内部复用级联删除逻辑。</p>
     *
     * @param conversationId 要删除的会话主键 ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Integer conversationId) {
        deleteConversationData(conversationId);
    }

    /**
     * 级联删除单条会话的关联数据。
     * <p>按固定顺序先删除子表记录（消息、Agent 日志、RAG 检索日志、复习记录），
     * 最后删除主表 conversation 记录，避免外键约束冲突。</p>
     *
     * @param conversationId 要删除的会话主键 ID
     */
    private void deleteConversationData(Integer conversationId) {
        // 事务边界说明：private 方法本身不开启新事务，复用调用方的事务上下文
        jdbcTemplate.update("DELETE FROM message WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM agent_log WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM rag_search_log WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM review_records WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM conversation WHERE id = ?", conversationId);
    }
}
