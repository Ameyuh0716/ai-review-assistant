package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.agent.tool.AgentTool;
import com.aiservice.aireviewassistant.agent.tool.ChainExecutor;
import com.aiservice.aireviewassistant.agent.tool.ToolContext;
import com.aiservice.aireviewassistant.agent.tool.ToolRegistry;
import com.aiservice.aireviewassistant.config.AppProperties;
import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.entity.AgentLog;
import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.entity.Message;
import com.aiservice.aireviewassistant.metrics.AgentMetrics;
import com.aiservice.aireviewassistant.service.AgentLogService;
import com.aiservice.aireviewassistant.service.ConversationService;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.aiservice.aireviewassistant.service.MessageService;
import com.aiservice.aireviewassistant.service.RagService;
import com.aiservice.aireviewassistant.service.ReviewRecordsService;
import com.aiservice.aireviewassistant.service.StudyPlanService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI Review Assistant 的核心 Agent 编排类。
 * <p>
 * 作为 Agent 的统一入口，负责：
 * <ul>
 *     <li>意图识别：结合本地快速规则与 LLM 工具 Schema 推理，判断用户请求意图；</li>
 *     <li>工具路由：根据识别到的意图从 {@link ToolRegistry} 选择并调用对应的 {@link AgentTool}；</li>
 *     <li>执行模式：支持同步非流式 {@link #chat} 与流式 SSE {@link #chatStream} 两种输出方式；</li>
 *     <li>消息持久化：保存用户消息、AI 回复、Agent 调用日志及复习记录；</li>
 *     <li>特殊链路：识别“先…再…然后…”式的工具链请求，并自动保存生成的学习计划。</li>
 * </ul>
 */
@Slf4j
@Service
public class ReviewAssistantAgent {

    /** 工具注册表，维护意图到具体工具的映射。 */
    private final ToolRegistry toolRegistry;

    /** Spring AI 聊天客户端，用于 LLM 意图识别。 */
    private final ChatClient chatClient;

    /** 会话服务，负责会话的创建、查询与更新。 */
    private final ConversationService conversationService;

    /** 消息服务，负责用户与助手消息的持久化。 */
    private final MessageService messageService;

    /** Agent 调用日志服务，用于记录每次请求的意图、参数、耗时与成败。 */
    private final AgentLogService agentLogService;

    /** 复习记录服务，将问答对沉淀到错题本/复习本。 */
    private final ReviewRecordsService reviewRecordsService;

    /** Prompt 模板渲染器，用于构造意图识别等提示词。 */
    private final PromptTemplate promptTemplate;

    /** Agent 指标收集器，统计工具调用、请求耗时、错误数等。 */
    private final AgentMetrics agentMetrics;

    /** 工具链执行器，处理“先…再…然后…”式的连续工具调用请求。 */
    private final ChainExecutor chainExecutor;

    /** JSON 序列化/反序列化工具，用于解析 LLM 返回的意图与记录参数。 */
    private final ObjectMapper objectMapper;

    /** 学习计划服务，用于在生成学习计划后自动归档。 */
    private final StudyPlanService studyPlanService;

    /** 课程服务，用于将消息中的课程前缀解析为课程 ID（学习统计课程覆盖率）。 */
    private final CoursesService coursesService;

    /** 业务配置，用于读取学习统计相关开关。 */
    private final AppProperties appProperties;

    /** 前端在选中课程时拼入消息首部的课程前缀，如 {@code [课程: 操作系统] 复习一下}。 */
    private static final Pattern COURSE_PREFIX_PATTERN = Pattern.compile("^\\[课程[:：]\\s*([^\\]]+)\\]");

    /**
     * 社交性短语（归一化后匹配）：命中则视为闲聊，不计入复习统计。
     * <p>包含问候、致谢、道别与询问助手身份等与学习内容无关的对话。</p>
     */
    private static final List<String> CASUAL_PHRASES = List.of(
        "你好", "您好", "嗨", "哈喽", "hello", "hi", "在吗", "在么", "在不在",
        "谢谢", "多谢", "感谢", "再见", "拜拜", "晚安", "早安", "早上好", "下午好", "晚上好",
        "好的", "好滴", "嗯", "哦", "ok", "okay", "哈哈", "嘿嘿", "嘻嘻",
        "你是谁", "你叫什么", "介绍一下你自己", "你能做什么", "你会做什么", "你能干什么"
    );

    /**
     * 纯闲聊的最大归一化长度。
     * <p>超过该长度说明消息包含实质内容（如“你好，什么是进程”），不再当作闲聊过滤。</p>
     */
    private static final int MAX_CASUAL_LENGTH = 5;

    /**
     * 构造核心 Agent，由 Spring 注入所有依赖组件。
     *
     * @param toolRegistry        工具注册表
     * @param chatClient          Spring AI 聊天客户端
     * @param conversationService 会话服务
     * @param messageService      消息服务
     * @param agentLogService     Agent 日志服务
     * @param reviewRecordsService 复习记录服务
     * @param promptTemplate      Prompt 模板渲染器
     * @param agentMetrics        指标收集器
     * @param chainExecutor       工具链执行器
     * @param objectMapper        JSON 工具
     * @param studyPlanService    学习计划服务
     * @param coursesService      课程服务
     * @param appProperties       业务配置（学习统计开关）
     */
    public ReviewAssistantAgent(ToolRegistry toolRegistry, ChatClient chatClient,
                                ConversationService conversationService, MessageService messageService,
                                AgentLogService agentLogService, ReviewRecordsService reviewRecordsService,
                                PromptTemplate promptTemplate, AgentMetrics agentMetrics,
                                ChainExecutor chainExecutor, ObjectMapper objectMapper,
                                StudyPlanService studyPlanService, CoursesService coursesService,
                                AppProperties appProperties) {
        this.toolRegistry = toolRegistry;
        this.chatClient = chatClient;
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.agentLogService = agentLogService;
        this.reviewRecordsService = reviewRecordsService;
        this.promptTemplate = promptTemplate;
        this.agentMetrics = agentMetrics;
        this.chainExecutor = chainExecutor;
        this.objectMapper = objectMapper;
        this.studyPlanService = studyPlanService;
        this.coursesService = coursesService;
        this.appProperties = appProperties;
    }

    /**
     * 意图识别结果内部记录。
     *
     * @param intent     识别到的意图标识，如 CHAT、QUIZ、PLAN 等
     * @param parameters 从用户消息中提取的工具参数键值对
     */
    private record IntentResult(String intent, Map<String, String> parameters) {}

    /**
     * 动态构建意图识别 Prompt。
     * <p>
     * 将当前工具 Schema、用户消息及最近历史消息注入模板，帮助 LLM 在上下文消歧后返回结构化意图。
     *
     * @param message 当前用户消息
     * @param history 当前会话历史消息
     * @return 渲染后的完整意图识别 Prompt
     */
    private String buildIntentPrompt(String message, List<Message> history) {
        // 把最近 5 条历史消息格式化为文本，作为 Prompt 的上下文部分
        String historyText = formatHistoryForIntent(history);
        return promptTemplate.render("intent-recognition.txt", Map.of(
            "toolSchemas", toolRegistry.buildToolSchemas(),
            "history", historyText,
            "userMessage", message
        ));
    }

    /**
     * 将最近历史消息格式化为意图识别 Prompt 可理解的文本。
     * <p>
     * 仅取最近 5 条消息，保留角色、内容与历史意图，避免 Prompt 过长。
     *
     * @param history 当前会话历史消息列表
     * @return 格式化后的历史消息文本；无历史时返回占位说明
     */
    private String formatHistoryForIntent(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "（无历史消息）";
        }
        StringBuilder sb = new StringBuilder();
        // 只保留最近 5 条，控制上下文长度与成本
        int start = Math.max(0, history.size() - 5);
        for (int i = start; i < history.size(); i++) {
            Message msg = history.get(i);
            String role = "user".equals(msg.getRole()) ? "用户" : "助手";
            sb.append(role).append("：").append(msg.getContent()).append("\n");
            // 若历史消息带有意图标记，一并暴露给 LLM 做消歧参考
            if (msg.getIntent() != null && !msg.getIntent().isEmpty()) {
                sb.append("（意图：").append(msg.getIntent()).append("）\n");
            }
        }
        return sb.toString();
    }

    /**
     * Agent 核心对话入口（普通非流式模式）。
     *
     * @param userMessage    用户输入消息
     * @param conversationId 会话 ID，可为 null（将自动创建新会话）
     * @return 处理完成后展示给用户的文本响应
     */
    public String chat(String userMessage, Integer conversationId) {
        return chat(userMessage, conversationId, null);
    }

    /**
     * Agent 核心对话入口（普通非流式模式，带用户身份）。
     * <p>
     * 完整执行链路：获取/创建会话 → 保存用户消息 → 加载历史 → 工具链检测 → 意图识别 →
     * 工具选择与校验 → 执行 → 保存 AI 回复、复习记录与日志 → 返回展示文本。
     *
     * @param userMessage    用户输入消息
     * @param conversationId 会话 ID，可为 null
     * @param userId         用户 ID，可为 null（用于学习计划自动归档等场景）
     * @return 展示给用户的文本响应；异常时返回友好错误提示
     */
    public String chat(String userMessage, Integer conversationId, Integer userId) {
        return chat(userMessage, conversationId, userId, null);
    }

    /**
     * Agent 核心对话入口（普通非流式模式，带用户身份与课程上下文）。
     *
     * @param userMessage    用户输入消息
     * @param conversationId 会话 ID，可为 null
     * @param userId         用户 ID，可为 null
     * @param courseId       当前选中的课程 ID，可为 null；用于绑定会话课程以便学习统计
     * @return 展示给用户的文本响应；异常时返回友好错误提示
     */
    public String chat(String userMessage, Integer conversationId, Integer userId, Integer courseId) {
        long startTime = System.currentTimeMillis();
        String intent = null;
        Map<String, String> params = null;
        Integer convId = null;
        Conversation conversation = null;
        boolean success = false;
        try {
            // 获取或创建会话，同时更新会话标题与最近活动时间
            conversation = getOrCreateConversation(conversationId, userMessage, userId, courseId);
            convId = conversation.getId();

            // 持久化用户原始消息，便于后续多轮记忆与审计
            saveMessage(convId, "user", userMessage, null);

            // 加载历史消息并复用，减少后续重复查询数据库
            List<Message> history = loadHistory(convId);

            // 检测是否为工具链请求（先...再...然后...）
            if (chainExecutor.isChainRequest(userMessage)) {
                log.debug("[Agent] 识别到工具链请求: {}", userMessage);
                ChainExecutor.ChainExecution execution = chainExecutor.execute(userMessage, convId, history);
                String chainResponse = execution.text();
                saveMessage(convId, "assistant", chainResponse, "CHAIN");
                saveReviewRecord(convId, conversation != null ? conversation.getUserId() : null,
                    "CHAIN", userMessage, chainResponse);
                saveAgentLog(convId, userMessage, "CHAIN", params, startTime, true, null);
                agentMetrics.incrementToolCall("CHAIN");
                // 链式请求中的计划步骤同样需要归档到学习计划板块
                savePlansFromChain(execution.stepResults(), userId);
                success = true;
                return chainResponse;
            }

            // 意图识别
            IntentResult result = recognizeIntent(userMessage, convId, history);
            intent = result.intent();
            params = result.parameters();
            log.debug("========== Agent 意图识别 ==========");
            log.debug("用户消息: {}", userMessage);
            log.debug("识别意图: {}", intent);
            log.debug("提取参数: {}", params);

            // 从注册表获取工具并执行
            AgentTool tool = toolRegistry.getTool(intent);
            if (tool == null) {
                tool = toolRegistry.getTool("CHAT");
            }

            ToolContext context = new ToolContext(userMessage, convId, params, history,
                userId, conversation != null ? conversation.getCourseId() : null);

            // 工具参数校验
            if (!tool.validate(context)) {
                String error = tool.getValidationError(context);
                return error != null ? error : "参数校验失败，请检查输入内容。";
            }

            log.debug("[Agent] 调用工具: {}", tool.getName());
            log.debug("================================");

            String rawResponse = tool.execute(context);
            String displayResponse = tool.formatForDisplay(rawResponse, context);
            if (displayResponse == null) {
                displayResponse = rawResponse;
            }

            // 保存AI回复（内部保存完整版，含答案；展示给用户隐藏版）
            saveMessage(convId, "assistant", rawResponse, intent);
            // 保存复习记录（闲聊不计入统计）
            saveReviewRecord(convId, conversation != null ? conversation.getUserId() : null,
                intent, userMessage, rawResponse);
            // 记录成功日志
            saveAgentLog(convId, userMessage, intent, params, startTime, true, null);
            // 记录指标
            agentMetrics.incrementToolCall(tool.getName());
            // 学习计划生成完毕后自动保存到学习计划板块
            if ("PLAN".equals(intent) && userId != null) {
                try {
                    String courseName = params != null ? params.getOrDefault("courseName", "未命名课程") : "未命名课程";
                    String availableDays = params != null ? params.getOrDefault("availableDays", "7天") : "7天";
                    studyPlanService.savePlan(userId, courseName, availableDays, rawResponse);
                    log.debug("[Agent] 学习计划已保存: userId={}, courseName={}", userId, courseName);
                } catch (Exception e) {
                    log.warn("[Agent] 自动保存学习计划失败: {}", e.getMessage());
                }
            }
            success = true;
            return displayResponse;
        } catch (Exception e) {
            log.error("[Agent] 处理请求时发生错误: {}", e.getMessage(), e);
            // 记录失败日志，保留异常信息用于排查
            saveAgentLog(convId, userMessage, intent, params, startTime, false, e.getMessage());
            return "抱歉，处理您的请求时出现了错误，请稍后再试。如果问题持续，请尝试简化描述。";
        } finally {
            // 无论成败都记录请求耗时与成功/失败指标
            long durationMs = System.currentTimeMillis() - startTime;
            agentMetrics.recordAgentRequest(intent, durationMs);
            agentMetrics.incrementAgentRequest(intent, success);
        }
    }

    /**
     * 将工具链中的 PLAN 步骤结果归档到学习计划板块。
     * <p>
     * 链式请求（如“先总结……再出题……然后做个计划”）的意图会被记录为 CHAIN，
     * 因此不会再走单意图的自动保存分支，需要在这里单独处理计划归档。
     * </p>
     *
     * @param stepResults 工具链各步骤执行明细
     * @param userId      用户 ID，为空时跳过归档
     */
    private void savePlansFromChain(List<ChainExecutor.ChainStepResult> stepResults, Integer userId) {
        if (userId == null || stepResults == null || stepResults.isEmpty()) {
            return;
        }
        for (ChainExecutor.ChainStepResult step : stepResults) {
            if (!"PLAN".equals(step.intent()) || step.response() == null || step.response().isBlank()) {
                continue;
            }
            try {
                Map<String, String> stepParams = step.parameters();
                String courseName = stepParams.getOrDefault("courseName", "未命名课程");
                String availableDays = stepParams.getOrDefault("availableDays", "");
                if (availableDays.isBlank()) {
                    availableDays = "7天";
                }
                studyPlanService.savePlan(userId, courseName, availableDays, step.response());
                log.debug("[Agent] 链式学习计划已保存: userId={}, courseName={}", userId, courseName);
            } catch (Exception e) {
                log.warn("[Agent] 保存链式学习计划失败: {}", e.getMessage());
            }
        }
    }

    /**
     * 兼容旧入口：不带会话 ID 时自动创建新会话。
     *
     * @param userMessage 用户输入消息
     * @return 展示给用户的文本响应
     */
    public String chat(String userMessage) {
        return chat(userMessage, null);
    }

    /**
     * Agent 核心对话入口（流式 SSE 模式）。
     *
     * @param userMessage    用户输入消息
     * @param conversationId 会话 ID，可为 null
     * @return 包含会话元数据与流式内容的 {@link Flux}
     */
    public Flux<String> chatStream(String userMessage, Integer conversationId) {
        return chatStream(userMessage, conversationId, null);
    }

    /**
     * Agent 核心对话入口（流式 SSE 模式，带用户身份）。
     * <p>
     * 执行链路与 {@link #chat(String, Integer, Integer)} 类似，但工具输出通过 {@link AgentTool#stream}
     * 以流式方式返回。首帧会发送会话元数据，供前端绑定 conversationId；内容帧结束后在
     * {@code doOnComplete} 中完成消息、复习记录、日志与学习计划的持久化。
     *
     * @param userMessage    用户输入消息
     * @param conversationId 会话 ID，可为 null
     * @param userId         用户 ID，可为 null
     * @return 包含会话元数据与流式内容的 {@link Flux}
     */
    public Flux<String> chatStream(String userMessage, Integer conversationId, Integer userId) {
        return chatStream(userMessage, conversationId, userId, false);
    }

    /**
     * Agent 核心对话入口（流式 SSE 模式，带用户身份与“重新生成”支持）。
     * <p>
     * 执行链路与 {@link #chat(String, Integer, Integer)} 类似，但工具输出通过 {@link AgentTool#stream}
     * 以流式方式返回。首帧会发送会话元数据，供前端绑定 conversationId；内容帧结束后在
     * {@code doOnComplete} 中完成消息、复习记录、日志与学习计划的持久化。
     * </p>
     *
     * @param userMessage      用户输入消息
     * @param conversationId   会话 ID，可为 null
     * @param userId           用户 ID，可为 null
     * @param reuseUserMessage 为 true 时视为“重新生成”：不重复保存用户消息，也不重复计入复习记录，
     *                         仅在提示词历史中保留一次该消息
     * @return 包含会话元数据与流式内容的 {@link Flux}
     */
    public Flux<String> chatStream(String userMessage, Integer conversationId, Integer userId,
                                   Boolean reuseUserMessage) {
        return chatStream(userMessage, conversationId, userId, reuseUserMessage, null);
    }

    /**
     * Agent 核心对话入口（流式 SSE 模式，带用户身份、重新生成与课程上下文）。
     *
     * @param userMessage      用户输入消息
     * @param conversationId   会话 ID，可为 null
     * @param userId           用户 ID，可为 null
     * @param reuseUserMessage 为 true 时视为“重新生成”：不重复保存用户消息，也不重复计入复习记录
     * @param courseId         当前选中的课程 ID，可为 null；用于绑定会话课程以便学习统计
     * @return 包含会话元数据与流式内容的 {@link Flux}
     */
    public Flux<String> chatStream(String userMessage, Integer conversationId, Integer userId,
                                   Boolean reuseUserMessage, Integer courseId) {
        return chatStream(userMessage, conversationId, userId, reuseUserMessage, courseId, null, null);
    }

    /**
     * Agent 核心对话入口（流式模式，支持“就地重生成”）。
     * <p>相比 5 参数版本额外支持两种<b>非破坏性</b>场景：</p>
     * <ul>
     *   <li>{@code assistantMessageId}：把本次生成的 AI 回复<b>覆盖写入</b>指定的历史消息行，
     *       而不是追加新消息。这样“编辑提问”与“重新生成”都不会删除任何历史记录，
     *       消息顺序（时间戳）也保持不变。</li>
     *   <li>{@code historyBeforeId}：只加载该消息之前的历史作为上下文，
     *       使编辑后的提问基于“编辑点之前的对话”重新生成，不被其后的旧回复干扰。</li>
     * </ul>
     *
     * @param userMessage        用户输入消息
     * @param conversationId     会话 ID，可为 null
     * @param userId             用户 ID，可为 null
     * @param reuseUserMessage   为 true 时不重复保存用户消息（内容已存在或已就地更新）
     * @param courseId           当前选中的课程 ID，可为 null
     * @param assistantMessageId 需要就地覆盖的 AI 消息 ID，为 null 时追加新消息
     * @param historyBeforeId    上下文截断点（只取 id 小于该值的历史消息），可为 null
     * @return 包含会话元数据与流式内容的 {@link Flux}
     */
    public Flux<String> chatStream(String userMessage, Integer conversationId, Integer userId,
                                   Boolean reuseUserMessage, Integer courseId,
                                   Integer assistantMessageId, Integer historyBeforeId) {
        long startTime = System.currentTimeMillis();
        String intent = null;
        Map<String, String> params = null;
        Integer convId = null;
        Conversation conversation = null;
        final boolean reuse = Boolean.TRUE.equals(reuseUserMessage);
        final Integer overwriteMessageId = assistantMessageId;
        try {
            conversation = getOrCreateConversation(conversationId, userMessage, userId, courseId);
            convId = conversation.getId();

            // 持久化用户原始消息（“编辑/重新生成”场景下消息已存在或已就地更新，避免重复保存）
            if (!reuse) {
                saveMessage(convId, "user", userMessage, null);
            }

            // 加载历史消息作为上下文；指定了截断点时只取该消息之前的历史
            List<Message> history = historyBeforeId != null
                ? loadHistoryBefore(convId, historyBeforeId)
                : loadHistory(convId);
            if (reuse && historyBeforeId == null) {
                history = trimTrailingUserMessage(history, userMessage);
            }

            // 第一个 SSE 事件：发送会话元数据，前端据此绑定 conversationId
            Flux<String> metaFlux = Flux.just("{\"conversationId\":" + convId + "}");

            // 检测是否为工具链请求（先...再...然后...）：逐步流式推送，避免多步串行等待超时
            if (chainExecutor.isChainRequest(userMessage)) {
                log.debug("[Agent] 流式模式识别到工具链请求: {}", userMessage);
                final Integer chainConvId = convId;
                final Conversation chainConversation = conversation;
                final List<ChainExecutor.ChainStepResult> chainResults = new ArrayList<>();
                StringBuilder chainBuffer = new StringBuilder();
                Flux<String> chainFlux = chainExecutor
                    .executeStream(userMessage, chainConvId, history, chainResults::addAll)
                    .doOnNext(chainBuffer::append)
                    .doOnComplete(() -> {
                        String fullText = chainBuffer.toString();
                        saveAssistantMessage(chainConvId, overwriteMessageId, fullText, "CHAIN");
                        // 重新生成场景不重复计入复习记录，避免统计数据虚高
                        if (!reuse) {
                            saveReviewRecord(chainConvId,
                                    chainConversation != null ? chainConversation.getUserId() : null,
                                    "CHAIN", userMessage, fullText);
                        }
                        saveAgentLog(chainConvId, userMessage, "CHAIN", null, startTime, true, null);
                        // 链式请求中的计划步骤同样需要归档到学习计划板块
                        savePlansFromChain(chainResults, userId);
                    })
                    // 多步串行耗时较长，给足超时时间并在超时后保留已生成内容
                    .timeout(Duration.ofSeconds(300), Flux.just("\n\n[系统提示] 步骤响应超时，请稍后重试。"));
                agentMetrics.incrementToolCall("CHAIN");
                return Flux.concat(metaFlux, chainFlux);
            }

            // 意图识别：复用统一入口
            IntentResult result = recognizeIntent(userMessage, convId, history);
            intent = result.intent();
            params = result.parameters();
            log.debug("========== Agent 流式意图识别 ==========");
            log.debug("用户消息: {}", userMessage);
            log.debug("识别意图: {}", intent);
            log.debug("提取参数: {}", params);

            // 从注册表获取工具；若意图未注册则回退到通用聊天工具
            AgentTool tool = toolRegistry.getTool(intent);
            if (tool == null) {
                tool = toolRegistry.getTool("CHAT");
            }

            // 传入用户与会话课程：工具据此把知识库检索限定在“该用户自己的课程”内，
            // 避免匿名或跨用户命中别人的资料。课程取会话上解析出的值（含 [课程: xxx] 前缀解析结果）。
            ToolContext context = new ToolContext(userMessage, convId, params, history,
                userId, conversation != null ? conversation.getCourseId() : null);

            // 工具参数校验，失败时直接返回错误提示
            if (!tool.validate(context)) {
                String error = tool.getValidationError(context);
                return Flux.just(error != null ? error : "参数校验失败，请检查输入内容。");
            }

            log.debug("[Agent] 流式调用工具: {}", tool.getName());
            log.debug("================================");

            // 获取工具流式输出
            Flux<String> responseFlux = tool.stream(context);
            agentMetrics.incrementToolCall(tool.getName());

            // 收集流式内容并保存；使用 final 变量供 Lambda 内部使用
            StringBuilder contentBuffer = new StringBuilder();
            final String finalIntent = intent;
            final Integer finalConvId = convId;
            final Map<String, String> finalParams = params;
            final Conversation finalConversation = conversation;
            final Integer finalUserId = userId;
            final boolean[] successFlag = {false};
            final AgentTool finalTool = tool;
            final ToolContext finalContext = context;
            Flux<String> contentFlux = responseFlux
                // 边收边拼，用于最终持久化完整回复；RAG 元数据帧仅用于前端展示，不写入消息内容
                .doOnNext(chunk -> {
                    if (chunk != null && chunk.startsWith(RagService.RagMeta.SSE_PREFIX)) {
                        return;
                    }
                    contentBuffer.append(chunk);
                })
                .doOnComplete(() -> {
                    successFlag[0] = true;
                    String fullResponse = contentBuffer.toString();
                    // 流式结束后一次性保存完整 AI 回复、复习记录与日志
                    // 编辑/重新生成场景：就地覆盖原消息行，保证历史记录一条不丢
                    saveAssistantMessage(finalConvId, overwriteMessageId, fullResponse, finalIntent);
                    // 重新生成场景不重复计入复习记录，避免统计数据虚高
                    if (!reuse) {
                        saveReviewRecord(finalConvId, finalConversation != null ? finalConversation.getUserId() : null,
                            finalIntent, userMessage, fullResponse);
                    }
                    saveAgentLog(finalConvId, userMessage, finalIntent, finalParams, startTime, true, null);
                    // 学习计划生成完毕后自动保存到学习计划板块
                    if ("PLAN".equals(finalIntent) && finalUserId != null) {
                        try {
                            String courseName = finalParams != null ? finalParams.getOrDefault("courseName", "未命名课程") : "未命名课程";
                            String availableDays = finalParams != null ? finalParams.getOrDefault("availableDays", "7天") : "7天";
                            studyPlanService.savePlan(finalUserId, courseName, availableDays, fullResponse);
                            log.debug("[Agent] 学习计划已保存: userId={}, courseName={}", finalUserId, courseName);
                        } catch (Exception e) {
                            log.warn("[Agent] 自动保存学习计划失败: {}", e.getMessage());
                        }
                    }
                })
                .doOnError(e -> {
                    agentMetrics.incrementLlmError();
                    saveAgentLog(finalConvId, userMessage, finalIntent, finalParams, startTime, false, e.getMessage());
                })
                .doOnTerminate(() -> {
                    long durationMs = System.currentTimeMillis() - startTime;
                    agentMetrics.recordAgentRequest(finalIntent, durationMs);
                    agentMetrics.incrementAgentRequest(finalIntent, successFlag[0]);
                })
                .timeout(Duration.ofSeconds(180), Flux.just("[系统提示] 响应超时，请稍后重试。"))
                .retryWhen(Retry.max(1).filter(e -> {
                    // 仅在尚未收到任何内容且为连接类错误时重试一次，避免重复输出
                    String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                    return contentBuffer.length() == 0 &&
                            (msg.contains("connection reset") || msg.contains("connection refused") ||
                             msg.contains("broken pipe") || msg.contains("unexpected end of stream"));
                }))
                .onErrorResume(e -> {
                    // 流式连接被重置/异常时，保存已生成的部分内容并给出友好提示
                    log.warn("[Agent] 流式响应异常，尝试优雅降级: {}", e.getMessage());
                    String partial = contentBuffer.toString().trim();
                    String fallback;
                    if (!partial.isEmpty()) {
                        saveAssistantMessage(finalConvId, overwriteMessageId,
                            partial + "\n\n[系统提示] 生成被网络中断，以上内容已保存。", finalIntent);
                        fallback = "\n\n[系统提示] 生成被网络中断，以上内容已保存，请重试或继续提问。";
                    } else {
                        fallback = "[系统提示] 网络连接不稳定，生成被中断。请稍后重试。";
                    }
                    return Flux.just(fallback);
                });
            // 先发元数据（conversationId），再发内容流
            // 对出题等场景，对外展示由工具自己决定的格式化版本；数据库仍保存完整版。
            return Flux.concat(metaFlux, contentFlux)
                .map(chunk -> {
                    String formatted = finalTool.formatForDisplay(chunk, finalContext);
                    return formatted != null ? formatted : chunk;
                });
        } catch (Exception e) {
            log.error("[Agent] 处理流式请求时发生错误: {}", e.getMessage(), e);
            long durationMs = System.currentTimeMillis() - startTime;
            agentMetrics.recordAgentRequest(intent, durationMs);
            agentMetrics.incrementAgentRequest(intent, false);
            agentMetrics.incrementLlmError();
            saveAgentLog(convId, userMessage, intent, params, startTime, false, e.getMessage());
            String errorMsg = "抱歉，处理您的请求时出现了错误，请稍后再试。";
            if (convId != null) {
                return Flux.concat(Flux.just("{\"conversationId\":" + convId + "}"), Flux.just(errorMsg));
            }
            return Flux.just(errorMsg);
        }
    }

    /**
     * 兼容旧入口：不带会话 ID 时自动创建新会话。
     *
     * @param userMessage 用户输入消息
     * @return 包含会话元数据与流式内容的 {@link Flux}
     */
    public Flux<String> chatStream(String userMessage) {
        return chatStream(userMessage, null);
    }

    /**
     * 统一意图识别入口。
     * <p>
     * 当存在历史消息时，优先走 LLM 识别，避免上下文相关问句被本地规则误识别；
     * 否则先尝试本地快速规则，无法命中再走 LLM。
     *
     * @param message        当前用户消息
     * @param conversationId 当前会话 ID（目前主要用于日志，未参与识别逻辑）
     * @param history        当前会话历史消息
     * @return 识别结果，包含意图与参数
     */
    private IntentResult recognizeIntent(String message, Integer conversationId, List<Message> history) {
        // 如果已有历史消息，优先走 LLM，避免上下文相关问句被本地规则误识别
        if (hasHistory(history)) {
            return analyzeIntentWithLlm(message, history);
        }
        return analyzeIntent(message, history);
    }

    /**
     * 基于 LLM + 工具 Schema 的意图识别。
     * <p>
     * 通过 {@link ChatClient} 发送包含工具 Schema 与上下文的 Prompt，
     * 从返回内容中提取 JSON 并解析意图与参数；若解析失败或意图不在注册表中则回退到关键词匹配。
     *
     * @param message 当前用户消息
     * @param history 当前会话历史消息
     * @return 识别到的意图与参数
     */
    private IntentResult analyzeIntentWithLlm(String message, List<Message> history) {
        try {
            String response = chatClient.prompt()
                .system("你是意图识别助手，只根据工具 Schema 返回 JSON，不要解释。")
                .user(buildIntentPrompt(message, history))
                .call()
                .content();

            // 提取 JSON 字符串并解析
            String json = extractJson(response);
            JsonNode root = objectMapper.readTree(json);

            String intent = root.path("intent").asText("CHAT").toUpperCase();
            Map<String, String> parameters = new HashMap<>();
            JsonNode paramsNode = root.path("parameters");
            if (paramsNode.isObject()) {
                paramsNode.fields().forEachRemaining(entry -> {
                    parameters.put(entry.getKey(), entry.getValue().asText(""));
                });
            }

            // 简单校验：若识别到的意图未在注册表中，则回退到关键词兜底
            AgentTool tool = toolRegistry.getTool(intent);
            if (tool == null) {
                return fallbackIntent(message);
            }
            return new IntentResult(intent, parameters);
        } catch (Exception e) {
            log.warn("[Agent] LLM 意图识别失败，回退到关键词匹配: {}", e.getMessage());
            return fallbackIntent(message);
        }
    }

    /**
     * 从 LLM 响应中提取 JSON 文本。
     * <p>
     * 兼容 LLM 把 JSON 包裹在代码块（```json ... ```）中的情况；
     * 若无法定位花括号，则返回原内容或空对象字符串。
     *
     * @param response LLM 原始响应内容
     * @return 提取出的 JSON 字符串
     */
    private String extractJson(String response) {
        if (response == null || response.isEmpty()) {
            return "{}";
        }
        String trimmed = response.trim();
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start != -1 && end != -1 && start < end) {
            return trimmed.substring(start, end + 1);
        }
        return trimmed;
    }

    /**
     * 双层意图识别入口：先本地规则，复杂语义再走 LLM。
     * <p>
     * 本地规则命中时可直接返回结果，降低延迟与 LLM 调用成本；
     * 未命中时委托给 {@link #analyzeIntentWithLlm}。
     *
     * @param message 当前用户消息
     * @param history 当前会话历史消息
     * @return 识别结果
     */
    private IntentResult analyzeIntent(String message, List<Message> history) {
        IntentResult fastResult = analyzeIntentFast(message);
        if (fastResult != null) {
            log.debug("[Agent] 本地规则命中意图: {}, 参数: {}", fastResult.intent(), fastResult.parameters());
            return fastResult;
        }
        return analyzeIntentWithLlm(message, history);
    }

    /**
     * 本地快速意图识别规则。
     * <p>
     * 对常见且结构清晰的关键词直接命中，跳过 LLM 以降低延迟与成本；
     * 按“出题 > 计划 > 总结 > 解释 > 问题 > 问候”的优先级匹配，无法命中时返回 null。
     *
     * @param message 当前用户消息
     * @return 本地规则命中结果；未命中返回 null
     */
    private IntentResult analyzeIntentFast(String message) {
        // 1. 出题意图：题目/练习/测试/出题/面试题/考题
        if (containsQuizKeyword(message)) {
            return new IntentResult("QUIZ", Map.of());
        }

        // 2. 复习计划意图：计划/安排/复习计划/规划
        if (containsPlanKeyword(message)) {
            return new IntentResult("PLAN", Map.of());
        }

        // 3. 总结意图（优先于普通疑问词判断）
        if (containsSummaryKeyword(message)) {
            return new IntentResult("SUMMARY", Map.of());
        }

        // 4. 解释意图（优先于普通疑问词判断）
        if (containsExplainKeyword(message)) {
            return new IntentResult("EXPLAIN", Map.of());
        }

        // 5. 问题意图（明确疑问词或问号）
        if (containsQuestionKeyword(message)) {
            return new IntentResult("QUESTION", Map.of());
        }

        // 6. 常见问候，直接走普通对话
        if (isGreeting(message)) {
            return new IntentResult("CHAT", Map.of());
        }

        // 本地规则无法确定，交给 LLM
        return null;
    }

    /**
     * 判断用户消息是否包含总结类关键词。
     *
     * @param message 用户输入消息
     * @return 包含“总结/归纳/概括”等词时返回 true
     */
    private boolean containsSummaryKeyword(String message) {
        return message.contains("总结") || message.contains("归纳") || message.contains("概括");
    }

    /**
     * 判断用户消息是否包含解释类关键词。
     *
     * @param message 用户输入消息
     * @return 包含“解释/什么意思/含义/区别/对比”等词时返回 true
     */
    private boolean containsExplainKeyword(String message) {
        return message.contains("解释") || message.contains("什么意思") || message.contains("含义")
            || message.contains("区别") || message.contains("对比");
    }

    /**
     * 判断用户消息是否包含出题/练习类关键词。
     *
     * @param message 用户输入消息
     * @return 包含“题目/练习/测试/出题/面试题/考题”等词时返回 true
     */
    private boolean containsQuizKeyword(String message) {
        return message.contains("题目") || message.contains("练习") || message.contains("测试")
            || message.contains("出题") || message.contains("面试题") || message.contains("考题");
    }

    /**
     * 判断用户消息是否包含学习计划/安排类关键词。
     *
     * @param message 用户输入消息
     * @return 包含“计划/安排/复习计划/规划”等词时返回 true
     */
    private boolean containsPlanKeyword(String message) {
        return message.contains("计划") || message.contains("安排") || message.contains("复习计划")
            || message.contains("规划");
    }

    /**
     * 判断用户消息是否包含疑问类关键词或问号。
     *
     * @param message 用户输入消息
     * @return 包含常见疑问词或中英文问号时返回 true
     */
    private boolean containsQuestionKeyword(String message) {
        return message.contains("?") || message.contains("？") || message.contains("什么")
            || message.contains("怎么") || message.contains("如何") || message.contains("为什么")
            || message.contains("哪些") || message.contains("吗");
    }

    /**
     * 判断用户消息是否为常见问候语。
     *
     * @param message 用户输入消息
     * @return 以“你好/您好/hello/hi/hey/在吗/在嘛”开头时返回 true
     */
    private boolean isGreeting(String message) {
        String lower = message.toLowerCase();
        return lower.matches("^(你好|您好|hello|hi|hey|在吗|在嘛).*");
    }

    /**
     * LLM 意图识别失败时的关键词兜底回退。
     * <p>
     * 当 {@link #analyzeIntentWithLlm} 抛出异常或返回未注册意图时，按关键词优先级返回保守意图，
     * 确保用户请求至少能被通用聊天或已知工具处理。
     *
     * @param message 用户输入消息
     * @return 兜底识别结果，意图不会为 null
     */
    private IntentResult fallbackIntent(String message) {
        String intent;
        if (message.contains("题目") || message.contains("练习") || message.contains("测试") || message.contains("出题")) {
            intent = "QUIZ";
        } else if (message.contains("计划") || message.contains("安排") || message.contains("复习计划")) {
            intent = "PLAN";
        } else if (message.contains("总结") || message.contains("归纳") || message.contains("概括")) {
            intent = "SUMMARY";
        } else if (message.contains("解释") || message.contains("什么意思") || message.contains("含义") || message.contains("区别")) {
            intent = "EXPLAIN";
        } else if (message.contains("?") || message.contains("？") || message.contains("什么") ||
                   message.contains("怎么") || message.contains("如何") || message.contains("为什么")) {
            intent = "QUESTION";
        } else {
            intent = "CHAT";
        }
        return new IntentResult(intent, Map.of());
    }

    /**
     * 获取已有会话或创建新会话。
     * <p>
     * 若传入有效的 conversationId，则校验存在性并更新最近活动时间；
     * 若标题仍为占位符（“新对话”或空），则使用当前用户消息前 30 字重命名。
     * 未传入或不存在时，新建会话并持久化。
     * </p>
     * <p>
     * 课程绑定：优先使用显式传入的 courseId；未传时尝试从消息的
     * {@code [课程: 名称]} 前缀解析（前端选中课程时自动添加），
     * 解析成功则写入 conversation.course_id，使复习记录带上课程，
     * 供学习统计的“课程覆盖率”维度使用。切换课程时会同步更新。
     * </p>
     *
     * @param conversationId 会话 ID，可为 null
     * @param userMessage    当前用户消息，用于生成会话标题与解析课程
     * @param userId         用户 ID，可为 null；为 null 时记为 anonymous
     * @param courseId       当前课程 ID，可为 null
     * @return 获取或创建后的会话对象
     */
    private Conversation getOrCreateConversation(Integer conversationId, String userMessage,
                                                 Integer userId, Integer courseId) {
        // 解析课程：显式参数优先，其次从 [课程: xxx] 前缀按名称查找该用户的课程
        Integer resolvedCourseId = courseId != null ? courseId : resolveCourseIdFromMessage(userMessage, userId);
        if (conversationId != null) {
            Conversation exist = conversationService.getById(conversationId);
            if (exist != null) {
                // 如果标题是占位符，根据第一条真实消息重命名
                if ("新对话".equals(exist.getTitle()) || exist.getTitle() == null || exist.getTitle().isEmpty()) {
                    String title = userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage;
                    exist.setTitle(title);
                }
                // 绑定/更新会话课程：用户切换课程后再对话时同步为最新课程
                if (resolvedCourseId != null && !resolvedCourseId.equals(exist.getCourseId())) {
                    exist.setCourseId(resolvedCourseId);
                }
                // 更新会话最近活动时间，保证会话列表排序准确
                exist.setUpdatedAt(LocalDateTime.now());
                conversationService.updateById(exist);
                return exist;
            }
        }
        // 创建新会话，标题取用户消息前 30 字，超出部分用省略号
        Conversation conversation = new Conversation();
        String title = userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage;
        conversation.setTitle(title);
        conversation.setUserId(userId != null ? String.valueOf(userId) : "anonymous");
        conversation.setCourseId(resolvedCourseId);
        conversationService.save(conversation);
        return conversation;
    }

    /**
     * 从用户消息的 {@code [课程: 名称]} 前缀解析课程 ID。
     * <p>
     * 前端在选中课程后会把课程名以该前缀拼入消息首部；此处按名称在该用户名下查找课程。
     * 解析失败（无前缀、课程不存在、未登录）时返回 null，不影响正常对话。
     * </p>
     *
     * @param userMessage 用户消息
     * @param userId      用户 ID，可为 null
     * @return 匹配到的课程 ID；无法解析时返回 null
     */
    private Integer resolveCourseIdFromMessage(String userMessage, Integer userId) {
        if (userMessage == null || userId == null) {
            return null;
        }
        Matcher matcher = COURSE_PREFIX_PATTERN.matcher(userMessage);
        if (!matcher.find()) {
            return null;
        }
        String courseName = matcher.group(1).trim();
        if (courseName.isEmpty()) {
            return null;
        }
        try {
            Courses course = coursesService.lambdaQuery()
                .eq(Courses::getUserId, userId)
                .eq(Courses::getName, courseName)
                .one();
            return course != null ? course.getId() : null;
        } catch (Exception e) {
            log.debug("[Agent] 解析课程名失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 保存单条聊天消息。
     * <p>
     * 对 AI 回复会记录识别到的意图，便于后续审计与多轮上下文追踪。
     * 保存失败仅记录 warn，不影响主流程返回。
     *
     * @param conversationId 所属会话 ID
     * @param role           消息角色：user / assistant
     * @param content        消息内容
     * @param intent         消息对应的意图，用户消息可为 null
     */
    private void saveMessage(Integer conversationId, String role, String content, String intent) {
        try {
            Message message = new Message();
            message.setConversationId(conversationId);
            message.setRole(role);
            message.setContent(content);
            message.setIntent(intent);
            messageService.save(message);
        } catch (Exception e) {
            log.warn("[Agent] 保存消息失败: {}", e.getMessage());
        }
    }

    /**
     * 保存或就地覆盖一条 AI 回复消息。
     * <p>用于“编辑提问”与“重新生成”场景：当传入已存在的消息 ID 时直接更新该行内容，
     * 不新增消息、也不删除其后的任何记录，从而完整保留对话历史与消息顺序；
     * 目标消息不存在（已删除）时自动降级为追加新消息。</p>
     *
     * @param conversationId 所属会话 ID
     * @param existingId     需要覆盖的 AI 消息 ID，为 null 时追加新消息
     * @param content        新生成的回复内容
     * @param intent         本次意图
     */
    private void saveAssistantMessage(Integer conversationId, Integer existingId, String content, String intent) {
        if (existingId != null) {
            try {
                Message exist = messageService.getById(existingId);
                if (exist != null) {
                    exist.setContent(content);
                    exist.setIntent(intent);
                    messageService.updateById(exist);
                    log.debug("[Agent] 已就地覆盖 AI 回复: messageId={}", existingId);
                    return;
                }
                log.warn("[Agent] 目标回复消息不存在，改为追加新消息: messageId={}", existingId);
            } catch (Exception e) {
                log.warn("[Agent] 覆盖 AI 回复失败，改为追加新消息: {}", e.getMessage());
            }
        }
        saveMessage(conversationId, "assistant", content, intent);
    }

    /**
     * 加载截断点之前的历史消息（编辑场景专用）。
     * <p>只取 id 小于截断点的消息，即“被编辑提问之前的对话”，
     * 保证重新生成时上下文与编辑点之后的历史无关。</p>
     *
     * @param conversationId  会话 ID
     * @param historyBeforeId 截断点消息 ID（不含该条）
     * @return 历史消息列表；无历史时返回空列表
     */
    private List<Message> loadHistoryBefore(Integer conversationId, Integer historyBeforeId) {
        if (historyBeforeId == null) {
            return loadHistory(conversationId);
        }
        return messageService.lambdaQuery()
            .eq(Message::getConversationId, conversationId)
            .lt(Message::getId, historyBeforeId)
            .orderByAsc(Message::getCreatedAt)
            .last("LIMIT 10")
            .list();
    }

    /**
     * 保存 Agent 调用日志。
     * <p>
     * 记录用户原始消息、识别意图、提取参数、请求耗时、执行成败及异常信息，
     * 用于后续监控、排错与效果评估。保存失败仅记录 warn，不影响主流程。
     *
     * @param conversationId 所属会话 ID
     * @param userMessage    用户原始消息
     * @param intent         识别意图
     * @param params         工具参数
     * @param startTime      请求开始时间戳（毫秒）
     * @param success        是否执行成功
     * @param errorMsg       错误信息，成功时可为 null
     */
    private void saveAgentLog(Integer conversationId, String userMessage, String intent,
                              Map<String, String> params, long startTime, boolean success, String errorMsg) {
        try {
            AgentLog log = new AgentLog();
            log.setConversationId(conversationId);
            log.setUserMessage(userMessage);
            log.setIntent(intent != null ? intent : "UNKNOWN");
            log.setParameters(objectMapper.writeValueAsString(params != null ? params : new HashMap<>()));
            log.setLatencyMs(System.currentTimeMillis() - startTime);
            log.setSuccess(success);
            log.setErrorMsg(errorMsg);
            log.setCreatedAt(LocalDateTime.now());
            agentLogService.save(log);
        } catch (Exception e) {
            log.warn("[Agent] 保存调用日志失败: {}", e.getMessage());
        }
    }

    /**
     * 将问答对沉淀到复习记录（错题本/复习本）。
     * <p>
     * 过滤规则：闲聊（问候、致谢、道别、询问助手身份等）不计入复习统计，
     * 避免“你好”这类无学习内容的对话虚增复习次数、活跃天数与综合评分；
     * 学习类意图（问答/出题/总结/解释/计划/链式）一律记录。
     * 是否过滤由 {@code app.stats.skip-casual-chat} 控制（默认开启）。
     * 保存失败仅记录 warn，避免影响主响应流程。
     *
     * @param conversationId 所属会话 ID
     * @param userId         用户 ID
     * @param intent         本次请求识别到的意图
     * @param question       问题/用户消息
     * @param answer         AI 生成的答案
     */
    private void saveReviewRecord(Integer conversationId, String userId, String intent,
                                  String question, String answer) {
        if (!shouldRecordReview(intent, question)) {
            log.debug("[Agent] 闲聊消息不计入复习统计: intent={}, message={}", intent, question);
            return;
        }
        try {
            reviewRecordsService.saveFromAgent(conversationId, userId, question, answer);
        } catch (Exception e) {
            log.warn("[Agent] 保存复习记录失败: {}", e.getMessage());
        }
    }

    /**
     * 判断本次对话是否应计入复习统计。
     * <p>
     * 仅对普通对话（CHAT 意图）应用闲聊过滤：学习类意图即使包含少量社交词也照常记录。
     * </p>
     *
     * @param intent      识别到的意图
     * @param userMessage 用户消息
     * @return 需要记录时返回 true
     */
    private boolean shouldRecordReview(String intent, String userMessage) {
        if (!"CHAT".equals(intent)) {
            return true;
        }
        // 配置缺失或关闭过滤时，保持原有行为（全部记录）
        AppProperties.Stats stats = appProperties != null ? appProperties.getStats() : null;
        if (stats != null && !stats.isSkipCasualChat()) {
            return true;
        }
        return !isCasualOnly(userMessage);
    }

    /**
     * 判断消息是否为纯闲聊（不含实质学习内容）。
     * <p>
     * 判定步骤：剥离前端拼接的 {@code [课程: xxx]} 前缀 → 归一化（去空白/标点/符号并转小写）
     * → 命中社交短语库即视为闲聊。仅当归一化后长度较短时才启用包含匹配，
     * 使“你好呀”“谢谢老师”等变体也能识别，同时保留“你好，什么叫进程”这类实质提问。
     * </p>
     *
     * @param userMessage 用户消息
     * @return 纯闲聊时返回 true
     */
    private boolean isCasualOnly(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return true;
        }
        String text = userMessage.replaceAll("^\\[课程[:：][^\\]]*\\]\\s*", "").trim();
        // \p{P} 标点、\p{S} 符号：归一化后仅保留文字与数字，避免标点影响匹配
        String normalized = text.replaceAll("[\\s\\p{P}\\p{S}]+", "").toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        boolean exact = CASUAL_PHRASES.stream().anyMatch(normalized::equals);
        boolean shortVariant = normalized.length() <= MAX_CASUAL_LENGTH
            && CASUAL_PHRASES.stream().anyMatch(normalized::contains);
        return exact || shortVariant;
    }

    /**
     * 加载当前会话最近 10 条历史消息。
     * <p>
     * 按创建时间升序排列，控制数量为 10 条，既保留足够上下文又避免 Prompt 过长。
     *
     * @param conversationId 会话 ID
     * @return 历史消息列表；无历史时返回空列表
     */
    private List<Message> loadHistory(Integer conversationId) {
        return messageService.lambdaQuery()
            .eq(Message::getConversationId, conversationId)
            .orderByAsc(Message::getCreatedAt)
            .last("LIMIT 10")
            .list();
    }

    /**
     * 判断已加载的历史消息是否非空。
     *
     * @param history 历史消息列表
     * @return 列表不为 null 且至少包含一条消息时返回 true
     */
    private boolean hasHistory(List<Message> history) {
        return history != null && !history.isEmpty();
    }

    /**
     * “重新生成”场景下去掉历史末尾重复的当前用户消息。
     * <p>
     * 重新生成时用户消息已入库且不会被再次保存，但工具在构建提示词时会自行拼接当前消息；
     * 若历史中保留该消息会导致同一句话出现两次，因此先裁掉末尾这条相同内容的消息。
     * </p>
     *
     * @param history     历史消息列表
     * @param userMessage 当前用户消息
     * @return 裁剪后的历史消息列表
     */
    private List<Message> trimTrailingUserMessage(List<Message> history, String userMessage) {
        if (history == null || history.isEmpty() || userMessage == null) {
            return history;
        }
        Message last = history.get(history.size() - 1);
        if ("user".equals(last.getRole()) && userMessage.equals(last.getContent())) {
            return new ArrayList<>(history.subList(0, history.size() - 1));
        }
        return history;
    }
}
