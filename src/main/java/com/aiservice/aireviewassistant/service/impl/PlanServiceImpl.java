package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.service.PlanService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * 复习计划服务实现类。
 *
 * <p>基于 Spring AI {@link ChatClient} 与 {@link PromptTemplate} 模板引擎，
 * 根据课程名称与可用天数生成个性化复习计划。同步方法启用缓存以避免重复调用大模型；
 * 流式方法直接返回 SSE 数据流，用于前端实时展示生成进度。</p>
 */
@Service
public class PlanServiceImpl implements PlanService {

    private final ChatClient chatClient;
    private final PromptTemplate promptTemplate;

    /**
     * 构造复习计划服务实现。
     *
     * @param chatClient     Spring AI 聊天客户端，用于发起同步/流式大模型调用
     * @param promptTemplate 提示词模板渲染器，用于加载并填充系统提示词与用户提示词
     */
    public PlanServiceImpl(ChatClient chatClient, PromptTemplate promptTemplate) {
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
    }

    /**
     * 根据课程和可用时间生成完整复习计划。
     *
     * <p>使用 {@code plan-user.txt} 模板渲染用户请求，使用 {@code plan-system.txt} 作为系统提示词，
     * 通过 {@link ChatClient} 发起同步调用并返回完整文本。结果被缓存到 {@code agentResults}，
     * 缓存键由课程名与可用天数组合而成，降低相同请求对大模型的重复调用。</p>
     *
     * @param courseName    课程名称
     * @param availableDays 可用复习天数
     * @return AI 生成的完整复习计划文本
     */
    @Override
    @Cacheable(value = "agentResults", key = "'plan:' + #courseName + ':' + #availableDays")
    public String createPlan(String courseName, String availableDays) {
        // 渲染用户提示词，注入课程名与可用天数变量
        String prompt = buildPlanPrompt(courseName, availableDays);
        // 渲染系统提示词，无需额外上下文变量
        String systemPrompt = promptTemplate.render("plan-system.txt", null);
        // 发起同步大模型调用并返回完整内容
        return chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .call()
            .content();
    }

    /**
     * 流式生成复习计划。
     *
     * <p>提示词构建逻辑与 {@link #createPlan(String, String)} 一致，但通过 {@code stream()} 方法
     * 以 Server-Sent Events 形式逐段返回 AI 生成内容，适用于前端打字机效果展示。
     * 流式调用不走缓存，确保每次都能实时响应。</p>
     *
     * @param courseName    课程名称
     * @param availableDays 可用复习天数
     * @return 按流式分片返回的复习计划文本
     */
    @Override
    public Flux<String> createPlanStream(String courseName, String availableDays) {
        // 渲染用户提示词，注入课程名与可用天数变量
        String prompt = buildPlanPrompt(courseName, availableDays);
        // 渲染系统提示词，无需额外上下文变量
        String systemPrompt = promptTemplate.render("plan-system.txt", null);
        // 发起流式大模型调用，返回 Flux<String> 供调用方订阅
        return chatClient.prompt()
            .system(systemPrompt)
            .user(prompt)
            .stream()
            .content();
    }

    /**
     * 构建复习计划用户提示词。
     *
     * @param courseName    课程名称
     * @param availableDays 可用复习天数
     * @return 渲染后的用户提示词文本
     */
    private String buildPlanPrompt(String courseName, String availableDays) {
        return promptTemplate.render("plan-user.txt", Map.of(
            "courseName", courseName,
            "availableDays", availableDays
        ));
    }
}
