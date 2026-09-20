package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.RagService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 总结工具。
 * <p>
 * 基于知识库检索上下文，为指定课程或章节生成总结、概括或要点提炼。
 */
@Component
public class SummaryTool implements AgentTool {

    /** 与 LLM 交互的聊天客户端。 */
    private final ChatClient chatClient;

    /** RAG 服务，用于检索与主题相关的知识库上下文。 */
    private final RagService ragService;

    /** Prompt 模板渲染器。 */
    private final PromptTemplate promptTemplate;

    /**
     * 构造总结工具。
     *
     * @param chatClient 聊天客户端
     * @param ragService RAG 服务
     * @param promptTemplate Prompt 模板渲染器
     */
    public SummaryTool(ChatClient chatClient, RagService ragService, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.ragService = ragService;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 返回工具名称 {@code SUMMARY}。
     *
     * @return 工具名称
     */
    @Override
    public String getName() {
        return "SUMMARY";
    }

    /**
     * 返回工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String getDescription() {
        return "基于知识库生成课程或章节总结";
    }

    /**
     * 返回参数 Schema。
     *
     * @return topic（课程或章节主题，必填）
     */
    @Override
    public String getParameterSchema() {
        return "topic（课程或章节主题，必填）";
    }

    /**
     * 同步生成总结。
     *
     * @param context 工具执行上下文
     * @return 生成的总结文本
     */
    @Override
    public String execute(ToolContext context) {
        String topic = extractTopic(context);
        String retrievedContext = ragService.retrieveContext(topic, context.getConversationId(),
            RagService.RagScope.of(context.getUserId(), context.getCourseId()));
        String systemPrompt = promptTemplate.render("summary-system.txt",
            Map.of("topic", topic, "context", retrievedContext));
        return chatClient.prompt()
            .system(systemPrompt)
            .user("请生成总结")
            .call()
            .content();
    }

    /**
     * 流式生成总结。
     * <p>首个帧为 RAG 检索元数据（{"__rag":...}），前端据此展示检索命中情况。</p>
     *
     * @param context 工具执行上下文
     * @return 流式总结片段（首帧为元数据帧）
     */
    @Override
    public Flux<String> stream(ToolContext context) {
        String topic = extractTopic(context);
        RagService.RagContext ragContext = ragService.retrieveContextWithMeta(topic,
            context.getConversationId(),
            RagService.RagScope.of(context.getUserId(), context.getCourseId()));
        String systemPrompt = promptTemplate.render("summary-system.txt",
            Map.of("topic", topic, "context", ragContext.context()));
        return Flux.concat(
            Flux.just(ragContext.meta().toSseJson()),
            chatClient.prompt()
                .system(systemPrompt)
                .user("请生成总结")
                .stream()
                .content()
        );
    }

    /**
     * 校验总结主题是否为空。
     *
     * @param context 工具执行上下文
     * @return 校验通过返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(ToolContext context) {
        String topic = extractTopic(context);
        return topic != null && !topic.trim().isEmpty();
    }

    /**
     * 获取校验失败时的错误提示。
     *
     * @param context 工具执行上下文
     * @return 校验失败提示；通过时返回 {@code null}
     */
    @Override
    public String getValidationError(ToolContext context) {
        String topic = extractTopic(context);
        if (topic == null || topic.trim().isEmpty()) {
            return "请告诉我你想总结哪个课程或章节。";
        }
        return null;
    }

    /**
     * 从参数或用户消息中提取总结主题。
     *
     * @param context 工具执行上下文
     * @return 提取的主题；无法提取时返回空字符串
     */
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
