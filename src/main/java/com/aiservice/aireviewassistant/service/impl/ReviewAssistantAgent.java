package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.agent.tool.AgentTool;
import com.aiservice.aireviewassistant.agent.tool.ChainExecutor;
import com.aiservice.aireviewassistant.agent.tool.ToolContext;
import com.aiservice.aireviewassistant.agent.tool.ToolRegistry;
import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.entity.AgentLog;
import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.entity.Message;
import com.aiservice.aireviewassistant.metrics.AgentMetrics;
import com.aiservice.aireviewassistant.service.AgentLogService;
import com.aiservice.aireviewassistant.service.ConversationService;
import com.aiservice.aireviewassistant.service.MessageService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Agent对话服务 - 意图识别+工具注册表+多轮记忆
@Slf4j
@Service
public class ReviewAssistantAgent {

    private final ToolRegistry toolRegistry;
    private final ChatClient chatClient;
    private final ConversationService conversationService;
    private final MessageService messageService;
    private final AgentLogService agentLogService;
    private final ReviewRecordsService reviewRecordsService;
    private final PromptTemplate promptTemplate;
    private final AgentMetrics agentMetrics;
    private final ChainExecutor chainExecutor;
    private final ObjectMapper objectMapper;
    private final StudyPlanService studyPlanService;

    public ReviewAssistantAgent(ToolRegistry toolRegistry, ChatClient chatClient,
                                ConversationService conversationService, MessageService messageService,
                                AgentLogService agentLogService, ReviewRecordsService reviewRecordsService,
                                PromptTemplate promptTemplate, AgentMetrics agentMetrics,
                                ChainExecutor chainExecutor, ObjectMapper objectMapper,
                                StudyPlanService studyPlanService) {
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
    }

    // 意图识别结果
    private record IntentResult(String intent, Map<String, String> parameters) {}

    // 动态构建意图识别 Prompt，传入最近历史消息以支持上下文消歧
    private String buildIntentPrompt(String message, List<Message> history) {
        String historyText = formatHistoryForIntent(history);
        return promptTemplate.render("intent-recognition.txt", Map.of(
            "toolSchemas", toolRegistry.buildToolSchemas(),
            "history", historyText,
            "userMessage", message
        ));
    }

    // 将最近历史消息格式化为意图识别 Prompt 可理解的文本
    private String formatHistoryForIntent(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "（无历史消息）";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 5);
        for (int i = start; i < history.size(); i++) {
            Message msg = history.get(i);
            String role = "user".equals(msg.getRole()) ? "用户" : "助手";
            sb.append(role).append("：").append(msg.getContent()).append("\n");
            if (msg.getIntent() != null && !msg.getIntent().isEmpty()) {
                sb.append("（意图：").append(msg.getIntent()).append("）\n");
            }
        }
        return sb.toString();
    }

    // Agent核心对话入口（普通模式）
    public String chat(String userMessage, Integer conversationId) {
        return chat(userMessage, conversationId, null);
    }

    // Agent核心对话入口（带用户身份）
    public String chat(String userMessage, Integer conversationId, Integer userId) {
        long startTime = System.currentTimeMillis();
        String intent = null;
        Map<String, String> params = null;
        Integer convId = null;
        Conversation conversation = null;
        boolean success = false;
        try {
            // 获取或创建会话
            conversation = getOrCreateConversation(conversationId, userMessage, userId);
            convId = conversation.getId();

            // 保存用户消息
            saveMessage(convId, "user", userMessage, null);

            // 加载历史消息并复用，减少重复查询
            List<Message> history = loadHistory(convId);

            // 检测是否为工具链请求（先...再...然后...）
            if (chainExecutor.isChainRequest(userMessage)) {
                log.debug("[Agent] 识别到工具链请求: {}", userMessage);
                String chainResponse = chainExecutor.executeChain(userMessage, convId, history);
                saveMessage(convId, "assistant", chainResponse, "CHAIN");
                saveReviewRecord(convId, conversation != null ? conversation.getUserId() : null, userMessage, chainResponse);
                saveAgentLog(convId, userMessage, "CHAIN", params, startTime, true, null);
                agentMetrics.incrementToolCall("CHAIN");
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

            ToolContext context = new ToolContext(userMessage, convId, params, history);

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
            // 保存复习记录
            saveReviewRecord(convId, conversation != null ? conversation.getUserId() : null, userMessage, rawResponse);
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
            // 记录失败日志
            saveAgentLog(convId, userMessage, intent, params, startTime, false, e.getMessage());
            return "抱歉，处理您的请求时出现了错误，请稍后再试。如果问题持续，请尝试简化描述。";
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            agentMetrics.recordAgentRequest(intent, durationMs);
            agentMetrics.incrementAgentRequest(intent, success);
        }
    }

    // 兼容旧入口：不带会话ID时自动创建新会话
    public String chat(String userMessage) {
        return chat(userMessage, null);
    }

    // Agent核心对话入口（流式模式）
    public Flux<String> chatStream(String userMessage, Integer conversationId) {
        return chatStream(userMessage, conversationId, null);
    }

    // Agent核心对话入口（流式模式，带用户身份）
    public Flux<String> chatStream(String userMessage, Integer conversationId, Integer userId) {
        long startTime = System.currentTimeMillis();
        String intent = null;
        Map<String, String> params = null;
        Integer convId = null;
        Conversation conversation = null;
        try {
            conversation = getOrCreateConversation(conversationId, userMessage, userId);
            convId = conversation.getId();

            // 保存用户消息
            saveMessage(convId, "user", userMessage, null);

            // 加载历史消息并复用
            List<Message> history = loadHistory(convId);

            // 第一个 SSE 事件：发送会话元数据，前端据此绑定 conversationId
            Flux<String> metaFlux = Flux.just("{\"conversationId\":" + convId + "}");

            // 检测是否为工具链请求（先...再...然后...）
            if (chainExecutor.isChainRequest(userMessage)) {
                log.debug("[Agent] 流式模式识别到工具链请求: {}", userMessage);
                String chainResponse = chainExecutor.executeChain(userMessage, convId, history);
                saveMessage(convId, "assistant", chainResponse, "CHAIN");
                saveReviewRecord(convId, conversation != null ? conversation.getUserId() : null, userMessage, chainResponse);
                saveAgentLog(convId, userMessage, "CHAIN", params, startTime, true, null);
                agentMetrics.incrementToolCall("CHAIN");
                return Flux.concat(metaFlux, Flux.just(chainResponse));
            }

            // 意图识别
            IntentResult result = recognizeIntent(userMessage, convId, history);
            intent = result.intent();
            params = result.parameters();
            log.debug("========== Agent 流式意图识别 ==========");
            log.debug("用户消息: {}", userMessage);
            log.debug("识别意图: {}", intent);
            log.debug("提取参数: {}", params);

            // 从注册表获取工具并执行
            AgentTool tool = toolRegistry.getTool(intent);
            if (tool == null) {
                tool = toolRegistry.getTool("CHAT");
            }

            ToolContext context = new ToolContext(userMessage, convId, params, history);

            // 工具参数校验
            if (!tool.validate(context)) {
                String error = tool.getValidationError(context);
                return Flux.just(error != null ? error : "参数校验失败，请检查输入内容。");
            }

            log.debug("[Agent] 流式调用工具: {}", tool.getName());
            log.debug("================================");

            Flux<String> responseFlux = tool.stream(context);
            agentMetrics.incrementToolCall(tool.getName());

            // 收集流式内容并保存
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
                .doOnNext(contentBuffer::append)
                .doOnComplete(() -> {
                    successFlag[0] = true;
                    String fullResponse = contentBuffer.toString();
                    saveMessage(finalConvId, "assistant", fullResponse, finalIntent);
                    saveReviewRecord(finalConvId, finalConversation != null ? finalConversation.getUserId() : null, userMessage, fullResponse);
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
                        saveMessage(finalConvId, "assistant", partial + "\n\n[系统提示] 生成被网络中断，以上内容已保存。", finalIntent);
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

    // 兼容旧入口
    public Flux<String> chatStream(String userMessage) {
        return chatStream(userMessage, null);
    }

    // 统一意图识别入口
    private IntentResult recognizeIntent(String message, Integer conversationId, List<Message> history) {
        // 如果已有历史消息，优先走 LLM，避免上下文相关问句被本地规则误识别
        if (hasHistory(history)) {
            return analyzeIntentWithLlm(message, history);
        }
        return analyzeIntent(message, history);
    }

    // 基于 LLM + 工具 Schema 的意图识别
    private IntentResult analyzeIntentWithLlm(String message, List<Message> history) {
        try {
            String response = chatClient.prompt()
                .system("你是意图识别助手，只根据工具 Schema 返回 JSON，不要解释。")
                .user(buildIntentPrompt(message, history))
                .call()
                .content();

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

            // 简单校验，不在列表则回退
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

    // 从 LLM 响应中提取 JSON（兼容被代码块包裹的情况）
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

    // 双层意图识别入口：先本地规则，复杂语义再走 LLM
    private IntentResult analyzeIntent(String message, List<Message> history) {
        IntentResult fastResult = analyzeIntentFast(message);
        if (fastResult != null) {
            log.debug("[Agent] 本地规则命中意图: {}, 参数: {}", fastResult.intent(), fastResult.parameters());
            return fastResult;
        }
        return analyzeIntentWithLlm(message, history);
    }

    // 本地快速规则：常见且结构清晰的关键词直接命中，跳过 LLM 以降低延迟
    private IntentResult analyzeIntentFast(String message) {
        // 1. 出题意图
        if (containsQuizKeyword(message)) {
            return new IntentResult("QUIZ", Map.of());
        }

        // 2. 复习计划意图
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

    // 判断是否包含总结关键词
    private boolean containsSummaryKeyword(String message) {
        return message.contains("总结") || message.contains("归纳") || message.contains("概括");
    }

    // 判断是否包含解释关键词
    private boolean containsExplainKeyword(String message) {
        return message.contains("解释") || message.contains("什么意思") || message.contains("含义")
            || message.contains("区别") || message.contains("对比");
    }

    // 判断是否包含出题关键词
    private boolean containsQuizKeyword(String message) {
        return message.contains("题目") || message.contains("练习") || message.contains("测试")
            || message.contains("出题") || message.contains("面试题") || message.contains("考题");
    }

    // 判断是否包含计划关键词
    private boolean containsPlanKeyword(String message) {
        return message.contains("计划") || message.contains("安排") || message.contains("复习计划")
            || message.contains("规划");
    }

    // 判断是否包含疑问关键词
    private boolean containsQuestionKeyword(String message) {
        return message.contains("?") || message.contains("？") || message.contains("什么")
            || message.contains("怎么") || message.contains("如何") || message.contains("为什么")
            || message.contains("哪些") || message.contains("吗");
    }

    // 判断是否常见问候
    private boolean isGreeting(String message) {
        String lower = message.toLowerCase();
        return lower.matches("^(你好|您好|hello|hi|hey|在吗|在嘛).*");
    }

    // LLM 失败时的关键词回退
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

    // 获取或创建会话（支持用户身份）
    private Conversation getOrCreateConversation(Integer conversationId, String userMessage, Integer userId) {
        if (conversationId != null) {
            Conversation exist = conversationService.getById(conversationId);
            if (exist != null) {
                // 如果标题是占位符，根据第一条真实消息重命名
                if ("新对话".equals(exist.getTitle()) || exist.getTitle() == null || exist.getTitle().isEmpty()) {
                    String title = userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage;
                    exist.setTitle(title);
                }
                // 更新会话时间
                exist.setUpdatedAt(LocalDateTime.now());
                conversationService.updateById(exist);
                return exist;
            }
        }
        // 创建新会话，标题取用户消息前30字
        Conversation conversation = new Conversation();
        String title = userMessage.length() > 30 ? userMessage.substring(0, 30) + "..." : userMessage;
        conversation.setTitle(title);
        conversation.setUserId(userId != null ? String.valueOf(userId) : "anonymous");
        conversationService.save(conversation);
        return conversation;
    }

    // 保存消息
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

    // 保存Agent调用日志
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

    // 保存复习记录
    private void saveReviewRecord(Integer conversationId, String userId, String question, String answer) {
        try {
            reviewRecordsService.saveFromAgent(conversationId, userId, question, answer);
        } catch (Exception e) {
            log.warn("[Agent] 保存复习记录失败: {}", e.getMessage());
        }
    }

    // 加载历史消息（最近10条）
    private List<Message> loadHistory(Integer conversationId) {
        return messageService.lambdaQuery()
            .eq(Message::getConversationId, conversationId)
            .orderByAsc(Message::getCreatedAt)
            .last("LIMIT 10")
            .list();
    }

    // 判断已加载的历史消息是否非空
    private boolean hasHistory(List<Message> history) {
        return history != null && !history.isEmpty();
    }
}
