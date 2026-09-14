package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.entity.Message;
import java.util.List;
import java.util.Map;

// 工具执行上下文：封装一次工具调用所需的全部信息
public class ToolContext {

    // 用户原始消息
    private final String userMessage;

    // 当前会话ID
    private final Integer conversationId;

    // 意图识别提取的参数
    private final Map<String, String> parameters;

    // 历史消息列表
    private final List<Message> history;

    public ToolContext(String userMessage, Integer conversationId,
                       Map<String, String> parameters, List<Message> history) {
        this.userMessage = userMessage;
        this.conversationId = conversationId;
        this.parameters = parameters;
        this.history = history;
    }

    public String getUserMessage() {
        return userMessage;
    }

    public Integer getConversationId() {
        return conversationId;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public List<Message> getHistory() {
        return history;
    }
}
