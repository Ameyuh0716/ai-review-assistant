package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 解释工具。
 * <p>
 * 针对错题、复杂概念或知识点提供详细解释；执行时会先通过 RAG 检索相关知识库上下文，
 * 再调用大模型生成回答。
 */
@Component
public class ExplainTool implements AgentTool {

    /** 与 LLM 交互的聊天客户端。 */
    private final ChatClient chatClient;

    /** RAG 服务，用于检索与概念相关的知识库上下文。 */
    private final RagService ragService;

    /** Prompt 模板渲染器。 */
    private final PromptTemplate promptTemplate;

    /**
     * 构造解释工具。
     *
     * @param chatClient 聊天客户端
     * @param ragService RAG 服务
     * @param promptTemplate Prompt 模板渲染器
     */
    public ExplainTool(ChatClient chatClient, RagService ragService, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 返回工具名称 {@code EXPLAIN}。
     *
     * @return 工具名称
     */
    @Override
    public String getName() {
        return "EXPLAIN";
    }

    /**
     * 返回工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String getDescription() {
        return "解释错题、复杂概念或知识点";
    }

    /**
     * 返回参数 Schema。
     *
     * @return concept（需要解释的概念、题目或知识点，必填）
     */
    @Override
    public String getParameterSchema() {
        return "concept（需要解释的概念、题目或知识点，必填）";
    }

    /**
     * 同步执行解释，返回完整解释文本。
     *
     * @param context 工具执行上下文
     * @return 大模型生成的解释内容
     */
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

    /**
     * 流式执行解释，返回逐字输出流。
     * <p>首个帧为 RAG 检索元数据（{"__rag":...}），前端据此展示检索命中情况。</p>
     *
     * @param context 工具执行上下文
     * @return 流式解释片段（首帧为元数据帧）
     */
    @Override
    public Flux<String> stream(ToolContext context) {
        String concept = extractConcept(context);
        RagService.RagContext ragContext = ragService.retrieveContextWithMeta(concept, context.getConversationId());
        String systemPrompt = promptTemplate.render("explain-system.txt",
            Map.of("concept", concept, "context", ragContext.context()));
        return Flux.concat(
            Flux.just(ragContext.meta().toSseJson()),
            chatClient.prompt()
                .system(systemPrompt)
                .user("请详细解释")
                .stream()
                .content()
        );
    }

    /**
     * 校验待解释的概念是否为空。
     *
     * @param context 工具执行上下文
     * @return 校验通过返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(ToolContext context) {
        String concept = extractConcept(context);
        return concept != null && !concept.trim().isEmpty();
    }

    /**
     * 获取校验失败时的错误提示。
     *
     * @param context 工具执行上下文
     * @return 校验失败提示；通过时返回 {@code null}
     */
    @Override
    public String getValidationError(ToolContext context) {
        String concept = extractConcept(context);
        if (concept == null || concept.trim().isEmpty()) {
            return "请告诉我你想解释哪个概念或题目。";
        }
        return null;
    }

    /**
     * 从参数或用户消息中提取需要解释的概念。
     *
     * @param context 工具执行上下文
     * @return 提取的概念；无法提取时返回空字符串
     */
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
