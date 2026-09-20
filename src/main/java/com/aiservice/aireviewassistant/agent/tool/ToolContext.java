package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.entity.Message;
import java.util.List;
import java.util.Map;

/**
 * 工具执行上下文。
 * <p>
 * 封装一次工具调用所需的全部信息，包括用户原始消息、当前会话 ID、意图识别提取的参数以及历史消息。
 */
public class ToolContext {

    // 用户原始消息
    private final String userMessage;

    // 当前会话ID
    private final Integer conversationId;

    // 意图识别提取的参数
    private final Map<String, String> parameters;

    // 历史消息列表
    private final List<Message> history;

    /** 当前登录用户 ID，为 null 表示匿名；用于隔离知识库检索范围。 */
    private final Integer userId;

    /** 当前选中的课程 ID，为 null 表示用户未指定课程；用于限定知识库检索范围。 */
    private final Integer courseId;

    /**
     * 构造工具执行上下文（不带用户/课程维度，知识库检索范围为空）。
     *
     * @param userMessage 用户原始消息
     * @param conversationId 当前会话 ID
     * @param parameters 意图识别提取的参数
     * @param history 历史消息列表
     */
    public ToolContext(String userMessage, Integer conversationId,
                       Map<String, String> parameters, List<Message> history) {
        this(userMessage, conversationId, parameters, history, null, null);
    }

    /**
     * 构造工具执行上下文。
     *
     * @param userMessage    用户原始消息
     * @param conversationId 当前会话 ID
     * @param parameters     意图识别提取的参数
     * @param history        历史消息列表
     * @param userId         当前登录用户 ID，可为 null
     * @param courseId       当前选中的课程 ID，可为 null
     */
    public ToolContext(String userMessage, Integer conversationId,
                       Map<String, String> parameters, List<Message> history,
                       Integer userId, Integer courseId) {
        this.userMessage = userMessage;
        this.conversationId = conversationId;
        this.parameters = parameters;
        this.history = history;
        this.userId = userId;
        this.courseId = courseId;
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

    public Integer getUserId() {
        return userId;
    }

    public Integer getCourseId() {
        return courseId;
    }
}
