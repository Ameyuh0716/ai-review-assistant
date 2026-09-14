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

// 会话生命周期管理：过期清理与归档
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationLifecycleService {

    private final ConversationService conversationService;
    private final AppProperties appProperties;
    private final JdbcTemplate jdbcTemplate;

    // 每天凌晨3点执行一次过期会话清理
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional(rollbackFor = Exception.class)
    public void cleanupExpiredConversations() {
        int expireDays = appProperties.getConversation().getExpireDays();
        LocalDateTime expireTime = LocalDateTime.now().minusDays(expireDays);

        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        wrapper.lt("updated_at", expireTime);
        List<Conversation> expired = conversationService.list(wrapper);

        if (expired.isEmpty()) {
            log.info("[Lifecycle] 无过期会话需要清理");
            return;
        }

        for (Conversation conversation : expired) {
            deleteConversationData(conversation.getId());
        }
        log.info("[Lifecycle] 已清理 {} 个过期会话", expired.size());
    }

    // 手动清理指定天数前的会话
    @Transactional(rollbackFor = Exception.class)
    public int cleanupBeforeDays(int days) {
        LocalDateTime expireTime = LocalDateTime.now().minusDays(days);
        QueryWrapper<Conversation> wrapper = new QueryWrapper<>();
        wrapper.lt("updated_at", expireTime);
        List<Conversation> expired = conversationService.list(wrapper);

        for (Conversation conversation : expired) {
            deleteConversationData(conversation.getId());
        }
        return expired.size();
    }

    // 删除单条会话及其关联数据
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(Integer conversationId) {
        deleteConversationData(conversationId);
    }

    // 级联删除会话关联数据
    private void deleteConversationData(Integer conversationId) {
        jdbcTemplate.update("DELETE FROM message WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM agent_log WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM rag_search_log WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM review_records WHERE conversation_id = ?", conversationId);
        jdbcTemplate.update("DELETE FROM conversation WHERE id = ?", conversationId);
    }
}
