package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.entity.Message;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

// 普通对话工具：处理问候、闲聊和上下文相关的普通问答
@Component
public class ChatTool implements AgentTool {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    public ChatTool(ChatClient chatClient, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public String getName() {
        return "CHAT";
    }

    @Override
    public String getDescription() {
        return "普通对话、问候或与课程无关的话题，也用于需要结合上下文的连续对话";
    }

    @Override
    public String getParameterSchema() {
        return "无";
    }

    @Override
    public String execute(ToolContext context) {
        String prompt = buildPrompt(context);
        return chatClient.prompt()
            .user(prompt)
            .call()
            .content();
    }

    @Override
    public Flux<String> stream(ToolContext context) {
        String prompt = buildPrompt(context);
        return chatClient.prompt()
            .user(prompt)
            .stream()
            .content();
    }

    // 构建带历史消息的 prompt
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
