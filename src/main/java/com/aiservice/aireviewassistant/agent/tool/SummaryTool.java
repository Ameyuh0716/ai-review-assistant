package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 总结工具：基于知识库生成课程或章节总结
@Component
public class SummaryTool implements AgentTool {

    private final ChatClient chatClient;
    private final RagService ragService;
    private final PromptTemplate promptTemplate;

    public SummaryTool(ChatClient chatClient, RagService ragService, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public String getName() {
        return "SUMMARY";
    }

    @Override
    public String getDescription() {
        return "基于知识库生成课程或章节总结";
    }

    @Override
    public String getParameterSchema() {
        return "topic（课程或章节主题，必填）";
    }

    @Override
    public String execute(ToolContext context) {
        String topic = extractTopic(context);
        String retrievedContext = ragService.retrieveContext(topic, context.getConversationId());
        String systemPrompt = promptTemplate.render("summary-system.txt",
            Map.of("topic", topic, "context", retrievedContext));
        return chatClient.prompt()
            .system(systemPrompt)
            .user("请生成总结")
            .call()
            .content();
    }

    @Override
    public Flux<String> stream(ToolContext context) {
        String topic = extractTopic(context);
        String retrievedContext = ragService.retrieveContext(topic, context.getConversationId());
        String systemPrompt = promptTemplate.render("summary-system.txt",
            Map.of("topic", topic, "context", retrievedContext));
        return chatClient.prompt()
            .system(systemPrompt)
            .user("请生成总结")
            .stream()
            .content();
    }

    @Override
    public boolean validate(ToolContext context) {
        String topic = extractTopic(context);
        return topic != null && !topic.trim().isEmpty();
    }

    @Override
    public String getValidationError(ToolContext context) {
        String topic = extractTopic(context);
        if (topic == null || topic.trim().isEmpty()) {
            return "请告诉我你想总结哪个课程或章节。";
        }
        return null;
    }

    // 从参数或用户消息中提取总结主题
    private String extractTopic(ToolContext context) {
        if (context.getParameters() != null && context.getParameters().get("topic") != null) {
            return context.getParameters().get("topic").trim();
        }
        String message = context.getUserMessage();

        // 模式一：关于 XXX 的总结
        Matcher aboutMatcher = Pattern.compile("关于(.*?)的?(?:总结|概括|概要|要点)").matcher(message);
        if (aboutMatcher.find()) {
            String topic = aboutMatcher.group(1).trim();
            if (!topic.isEmpty()) {
                return topic;
            }
        }

        // 模式二：总结[一下] XXX
        if (Pattern.compile("^(?:请|帮我|给我|生成|来一份|来一段)?\\s*(?:总结|概括|概要|要点)").matcher(message).find()) {
            String topic = message.replaceAll("^(?:请|帮我|给我|生成|来一份|来一段)?\\s*", "")
                                  .replaceAll("^(?:总结|概括|概要|要点)(?:一下|一份)?\\s*", "")
                                  .replaceAll("[?？]$", "")
                                  .trim();
            if (!topic.isEmpty() && !"一下".equals(topic)) {
                return topic;
            }
        }

        return "";
    }
}
