package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.entity.Message;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 普通对话工具
 * 
 * 处理问候、闲聊以及需要结合上下文的连续普通问答；不依赖知识库检索，直接通过大模型生成回复。
 */
@Component
public class ChatTool implements AgentTool {

    private final ChatClient chatClient;

    private final PromptTemplate promptTemplate;

    /**
     * 构造普通对话工具。
     *
     * @param chatClient 聊天客户端
     * @param promptTemplate Prompt 模板渲染器
     */
    public ChatTool(ChatClient chatClient, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 返回工具名称 {@code CHAT}。
     *
     * @return 工具名称
     */
    @Override
    public String getName() {
        return "CHAT";
    }

    /**
     * 返回工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String getDescription() {
        return "普通对话、问候或与课程无关的话题，也用于需要结合上下文的连续对话";
    }

    /**
     * 返回参数 Schema；普通对话无需额外参数。
     *
     * @return "无"
     */
    @Override
    public String getParameterSchema() {
        return "无";
    }

    /**
     * 同步执行普通对话，返回完整回复。
     *
     * @param context 工具执行上下文
     * @return 大模型生成的回复文本
     */
    @Override
    public String execute(ToolContext context) {
        String prompt = buildPrompt(context);
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }

    /**
     * 流式执行普通对话，返回逐字输出流。
     *
     * @param context 工具执行上下文
     * @return 流式回复片段
     */
    @Override
    public Flux<String> stream(ToolContext context) {
        String prompt = buildPrompt(context);
        return chatClient.prompt()
            .user(prompt)
            .stream()
            .content();
    }

    /**
     * 构建带历史消息的 prompt。
     * <p>
     * 渲染系统提示后，按"角色：内容"格式拼接历史消息与当前用户消息。
     *
     * @param context 工具执行上下文
     * @return 最终提交给大模型的 prompt 文本
     */
    private String buildPrompt(ToolContext context) {
        String systemPrompt = promptTemplate.render("chat-system.txt", null);
        StringBuilder promptBuilder = new StringBuilder(systemPrompt).append("\n\n");
        if (context.getHistory() != null && !context.getHistory().isEmpty()) {
            promptBuilder.append(promptTemplate.render("chat-user-prefix.txt", null));
            for (Message msg : context.getHistory()) {
                String roleName = "user".equals(msg.getRole()) ? "学生" : "助手";
                promptBuilder.append(roleName).append("：").append(msg.getContent()).append("\n");
            }
        }
        promptBuilder.append("\n学生：").append(context.getUserMessage()).append("\n助手：");
        return promptBuilder.toString();
    }
}
