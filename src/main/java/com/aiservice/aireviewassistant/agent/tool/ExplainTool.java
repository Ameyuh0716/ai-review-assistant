package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.regex.Pattern;

// 解释工具：解释错题或复杂知识点
@Component
public class ExplainTool implements AgentTool {

    private final ChatClient chatClient;
    private final RagService ragService;
    private final PromptTemplate promptTemplate;

    public ExplainTool(ChatClient chatClient, RagService ragService, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.promptTemplate = promptTemplate;
    }

    @Override
    public String getName() {
        return "EXPLAIN";
    }

    @Override
    public String getDescription() {
        return "解释错题、复杂概念或知识点";
    }

    @Override
    public String getParameterSchema() {
        return "concept（需要解释的概念、题目或知识点，必填）";
    }

    @Override
    public String execute(ToolContext context) {
        String concept = extractConcept(context);
        String retrievedContext = ragService.retrieveContext(concept, context.getConversationId());
        String systemPrompt = promptTemplate.render("explain-system.txt",
            Map.of("concept", concept, "context", retrievedContext));
        return chatClient.prompt()
            .system(systemPrompt)
            .user("请详细解释")
            .call()
            .content();
    }

    @Override
    public Flux<String> stream(ToolContext context) {
        String concept = extractConcept(context);
        String retrievedContext = ragService.retrieveContext(concept, context.getConversationId());
        String systemPrompt = promptTemplate.render("explain-system.txt",
            Map.of("concept", concept, "context", retrievedContext));
        return chatClient.prompt()
            .system(systemPrompt)
            .user("请详细解释")
            .stream()
            .content();
    }

    @Override
    public boolean validate(ToolContext context) {
        String concept = extractConcept(context);
        return concept != null && !concept.trim().isEmpty();
    }

    @Override
    public String getValidationError(ToolContext context) {
        String concept = extractConcept(context);
        if (concept == null || concept.trim().isEmpty()) {
            return "请告诉我你想解释哪个概念或题目。";
        }
        return null;
    }

    // 从参数或用户消息中提取需要解释的内容
    private String extractConcept(ToolContext context) {
        if (context.getParameters() != null && context.getParameters().get("concept") != null) {
            return context.getParameters().get("concept").trim();
        }
        String message = context.getUserMessage();

        // 模式：解释[一下] XXX
        if (Pattern.compile("^(?:请|帮我|给我|详细)?\\s*解释(?:一下|详细)?").matcher(message).find()) {
            String concept = message.replaceAll("^(?:请|帮我|给我|详细)?\\s*", "")
                                    .replaceAll("^解释(?:一下|详细)?\\s*", "")
                                    .replaceAll("[?？]$", "")
                                    .trim();
            if (!concept.isEmpty() && !"一下".equals(concept)) {
                return concept;
            }
        }

        return "";
    }
}
