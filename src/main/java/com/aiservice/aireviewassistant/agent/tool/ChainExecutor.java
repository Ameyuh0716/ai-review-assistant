package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.config.PromptTemplate;
import com.aiservice.aireviewassistant.entity.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 工具链执行器。
 * 
 * 处理用户以"先……再……然后……"形式表达的多步复杂请求。拆解策略分两层：
 * 
 * LLM 拆解（首选）：借助 {@code chain-planning.txt} 提示词把自然语言拆成结构化步骤，
 * 能正确处理"再出几道题""给我设计个复习计划"这类省略了主语的后续步骤
 * 规则拆解（兜底）：LLM 不可用或返回非法 JSON 时，退化为连接词切分 + 关键词意图推断。</li>
 * 
 * 执行阶段支持主题在步骤之间继承：后续步骤未指明主题时自动沿用上一步的主题。
 * 
 */
@Slf4j
@Component
public class ChainExecutor {

    /** 单次请求最多拆解的步骤数，防止异常输入产生过多步骤。 */
    private static final int MAX_CHAIN_STEPS = 3;

    /**
     * 链式步骤未明确指定题量时的默认值。
     * <p>中文里“出几道题”表示多道而非 1 道，给一个更符合直觉的默认值。</p>
     */
    private static final int DEFAULT_CHAIN_QUIZ_COUNT = 3;

    /** 题目数量参数名。 */
    private static final String PARAM_COUNT = "count";

    /** 规则兜底时用于切分步骤的连接词。 */
    private static final String[] SEPARATORS = {"再", "然后", "接着"};

    /** 可承载"主题"语义的参数名，用于步骤间主题继承。 */
    private static final List<String> TOPIC_KEYS = List.of("topic", "courseName", "concept");

    /** 主题剥离规则（兜底路径使用）：去掉数量短语。 */
    private static final Pattern QUANTITY_PATTERN =
            Pattern.compile("\\d+\\s*[道个份天题]|[一二两三四五六七八九十]\\s*[道个份天]|几\\s*[道个份]");

    //去掉标点与空白
    private static final Pattern PUNCTUATION_PATTERN =
            Pattern.compile("[\\p{Punct}\\s，。、；：！？（）【】“”‘’《》]+");

    //去掉句首的客套话与动词
    private static final Pattern LEADING_NOISE_PATTERN =
            Pattern.compile("^(先|再|然后|接着|请|帮我|帮忙|麻烦|给我|为我|生成|设计|制定|做|出|来一段|来|关于|一下|一份|一个|总结|概括|概要|列举|说说)+");

    //去掉句尾的泛化名词与语气词
    private static final Pattern TRAILING_NOISE_PATTERN =
            Pattern.compile("(总结|概括|概要|复习内容|复习计划|复习安排|复习|内容|计划|安排|规划|练习|测试|题目|题|一下|一份|一个|的|吧|了|呢|吗)+$");

    private final ToolRegistry toolRegistry;

    private final ChatClient chatClient;

    private final PromptTemplate promptTemplate;

    /** JSON 解析工具，用于解析 LLM 返回的步骤数组。 */
    private final ObjectMapper objectMapper;

    /**
     * 构造工具链执行器。
     *
     * @param toolRegistry   工具注册表
     * @param chatClient     聊天客户端
     * @param promptTemplate Prompt 模板渲染器
     * @param objectMapper   JSON 解析工具
     */
    public ChainExecutor(ToolRegistry toolRegistry,
                         ChatClient chatClient,
                         PromptTemplate promptTemplate,
                         ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.chatClient = chatClient;
        this.promptTemplate = promptTemplate;
        this.objectMapper = objectMapper;
    }

    // ============================================================
    // 请求判定
    // ============================================================

    /**
     * 判断用户消息是否为链式多步请求。
     * <p>
     * 命中条件（满足其一）：
     * <ul>
     *   <li>同时出现"先"与顺序连接词（再/然后/接着）；</li>
     *   <li>出现顺序连接词，且消息中至少包含两种不同的动作意图
     *       （如"总结一下操作系统，然后出几道题"）。</li>
     * </ul>
     * 单独一句"再来几道题"只含一种动作，仍按单意图处理，以保留"复用上一轮主题"的行为。
     * </p>
     *
     * @param message 用户原始消息
     * @return 是链式请求返回 {@code true}，否则返回 {@code false}
     */
    public boolean isChainRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        // 排除"先说"、"先问"等非工具链用法
        String cleaned = message.replaceAll("先说|先问|先聊|先谈|先讲", "");
        boolean hasSequential = cleaned.contains("然后") || cleaned.contains("接着")
                || Pattern.compile("再(出|做|总|解|讲|生成|列|说)").matcher(cleaned).find();
        if (!hasSequential) {
            return false;
        }
        if (cleaned.contains("先")) {
            return true;
        }
        // 没有"先"时，要求至少出现两种不同的动作意图，避免把普通的追问误判为多步请求
        return countActionKinds(cleaned) >= 2;
    }

    /**
     * 统计消息中出现的动作意图种类数量。
     *
     * @param message 用户消息
     * @return 命中的意图种类数
     */
    private int countActionKinds(String message) {
        int kinds = 0;
        if (containsSummaryKeyword(message)) {
            kinds++;
        }
        if (containsQuizKeyword(message)) {
            kinds++;
        }
        if (containsPlanKeyword(message)) {
            kinds++;
        }
        if (containsExplainKeyword(message)) {
            kinds++;
        }
        return kinds;
    }

    // ============================================================
    // 步骤拆解
    // ============================================================

    /**
     * 拆解用户消息为有序步骤：优先使用 LLM，失败时回退到规则拆解。
     *
     * @param message 用户原始消息
     * @param history 历史消息（供 LLM 消歧）
     * @return 有序步骤列表；无法拆解时返回空列表
     */
    private List<ChainStep> resolveSteps(String message, List<Message> history) {
        List<ChainStep> steps = parseChainWithLlm(message, history);
        if (steps.isEmpty()) {
            log.debug("[Chain] LLM 未返回可用步骤，回退到规则拆解");
            steps = parseChain(message);
        }
        List<ChainStep> normalized = new ArrayList<>(steps.size());
        for (ChainStep step : steps) {
            normalized.add(normalizeStep(step));
        }
        return normalized;
    }

    /**
     * 规范化步骤参数。
     * <p>
     * 目前仅处理 QUIZ 的题量。链式步骤必须携带明确数量，否则 {@link QuizTool} 会退化为
     * 扫描整条用户消息，而整条消息还包含其他步骤的文本，可能把"设计一个计划"里的
     * "一个"误读成 1 道题（实测 Bug）。
     * </p>
     *
     * @param step 待规范化的步骤
     * @return 规范化后的步骤
     */
    private ChainStep normalizeStep(ChainStep step) {
        if (!"QUIZ".equals(step.intent())) {
            return step;
        }
        Map<String, String> params = new HashMap<>(step.parameters());
        String count = params.get(PARAM_COUNT);
        if (count == null || count.isBlank()) {
            params.put(PARAM_COUNT, String.valueOf(DEFAULT_CHAIN_QUIZ_COUNT));
        }
        return new ChainStep(step.intent(), params, step.tool());
    }

    /**
     * 基于 LLM 的步骤拆解。
     * <p>
     * 通过 {@code chain-planning.txt} 提示词要求模型返回 JSON 数组，
     * 解析后映射为实际的工具实例；任何异常都返回空列表以便上层回退。
     * </p>
     *
     * @param message 用户原始消息
     * @param history 历史消息
     * @return 解析出的步骤列表；失败时返回空列表
     */
    private List<ChainStep> parseChainWithLlm(String message, List<Message> history) {
        try {
            // 使用 HashMap 而非 Map.of：后者遇到 null 值会抛 NPE，
            // 会把“提示词变量缺失”这类问题掩盖成“静默回退到规则拆解”
            Map<String, String> variables = new HashMap<>();
            variables.put("toolSchemas", toolRegistry.buildToolSchemas());
            variables.put("history", formatHistory(history));
            variables.put("userMessage", message);
            String prompt = promptTemplate.render("chain-planning.txt", variables);
            String response = chatClient.prompt()
                    .system("你是任务规划助手，只输出 JSON 数组，不要解释。")
                    .user(prompt)
                    .call()
                    .content();
            List<ChainStep> steps = parseStepsJson(response);
            if (!steps.isEmpty()) {
                log.debug("[Chain] LLM 拆解出 {} 个步骤: {}", steps.size(),
                        steps.stream().map(s -> s.intent() + s.parameters()).toList());
            }
            return steps;
        } catch (Exception e) {
            log.warn("[Chain] LLM 步骤拆解失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 解析 LLM 返回的步骤 JSON 数组。
     *
     * @param response LLM 原始响应
     * @return 解析出的步骤列表；格式非法时返回空列表
     */
    private List<ChainStep> parseStepsJson(String response) {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        String trimmed = response.trim();
        int start = trimmed.indexOf('[');
        int end = trimmed.lastIndexOf(']');
        if (start == -1 || end <= start) {
            return List.of();
        }
        try {
            JsonNode array = objectMapper.readTree(trimmed.substring(start, end + 1));
            if (!array.isArray()) {
                return List.of();
            }
            List<ChainStep> steps = new ArrayList<>();
            for (JsonNode node : array) {
                if (steps.size() >= MAX_CHAIN_STEPS) {
                    break;
                }
                String intent = node.path("intent").asText("").trim().toUpperCase();
                AgentTool tool = intent.isEmpty() ? null : toolRegistry.getTool(intent);
                if (tool == null) {
                    log.debug("[Chain] 忽略无法识别的步骤意图: {}", intent);
                    continue;
                }
                Map<String, String> params = new HashMap<>();
                JsonNode paramsNode = node.path("parameters");
                if (paramsNode.isObject()) {
                    paramsNode.fields().forEachRemaining(entry ->
                            params.put(entry.getKey(), entry.getValue().asText("")));
                }
                steps.add(new ChainStep(intent, params, tool));
            }
            return steps;
        } catch (Exception e) {
            log.warn("[Chain] 步骤 JSON 解析失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 规则兜底拆解：按连接词切分文本，并按关键词推断每一步的意图。
     * <p>
     * 不要求消息以"先"开头，只要包含"先"就从该处开始切分，使"帮我先总结……再……"同样可用。
     *
     * @param message 用户原始消息
     * @return 解析后的步骤列表；无法解析时返回空列表
     */
    public List<ChainStep> parseChain(String message) {
        List<ChainStep> steps = new ArrayList<>();
        if (message == null || message.isBlank()) {
            return steps;
        }
        String remaining = message.trim();

        // 从第一个"先"之后开始切分；没有"先"则整体从头开始
        int firstIndex = remaining.indexOf('先');
        if (firstIndex >= 0) {
            remaining = remaining.substring(firstIndex + 1).trim();
        }

        // 按"再"、"然后"、"接着"分割
        while (!remaining.isEmpty()) {
            Separator sep = findFirstSeparator(remaining);
            String part;
            if (sep == null) {
                part = remaining;
                remaining = "";
            } else {
                part = remaining.substring(0, sep.index()).trim();
                remaining = remaining.substring(sep.index() + sep.text().length()).trim();
            }

            if (!part.isEmpty()) {
                ChainStep step = inferStep(part);
                if (step != null) {
                    steps.add(step);
                }
            }

            // 最多解析 3 步，防止异常输入导致过多步骤
            if (steps.size() >= MAX_CHAIN_STEPS) {
                break;
            }
        }

        return steps;
    }

    // 查找第一个链式分隔符
    private Separator findFirstSeparator(String text) {
        int firstIndex = -1;
        String firstSep = null;
        for (String sep : SEPARATORS) {
            int index = text.indexOf(sep);
            if (index != -1 && (firstIndex == -1 || index < firstIndex)) {
                firstIndex = index;
                firstSep = sep;
            }
        }
        return firstSep == null ? null : new Separator(firstIndex, firstSep);
    }

    // 分隔符位置记录
    private record Separator(int index, String text) {
    }

    // 根据步骤文本推断意图和参数
    private ChainStep inferStep(String text) {
        String intent = "CHAT";
        Map<String, String> params = Map.of();

        if (containsSummaryKeyword(text)) {
            intent = "SUMMARY";
            params = Map.of("topic", extractTopic(text));
        } else if (containsQuizKeyword(text)) {
            intent = "QUIZ";
            params = Map.of("topic", extractTopic(text), "count", extractCount(text));
        } else if (containsPlanKeyword(text)) {
            intent = "PLAN";
            params = Map.of("courseName", extractTopic(text), "availableDays", extractDays(text));
        } else if (containsExplainKeyword(text) || containsQuestionKeyword(text)) {
            intent = "EXPLAIN";
            params = Map.of("concept", extractTopic(text));
        }

        AgentTool tool = toolRegistry.getTool(intent);
        if (tool == null) {
            return null;
        }
        return new ChainStep(intent, params, tool);
    }

    // ============================================================
    // 执行
    // ============================================================

    /**
     * 顺序执行拆解后的工具链（同步模式）。
     *
     * @param userMessage    用户原始消息
     * @param conversationId 当前会话 ID
     * @param history        历史消息列表
     * @return 执行结果，包含拼接文本与每个步骤的明细
     */
    public ChainExecution execute(String userMessage, Integer conversationId, List<Message> history) {
        List<ChainStep> steps = resolveSteps(userMessage, history);
        if (steps.isEmpty()) {
            return new ChainExecution("未能识别出多步请求，请分步描述您的需求。", List.of());
        }

        String inheritedTopic = null;
        StringBuilder text = new StringBuilder();
        List<ChainStepResult> results = new ArrayList<>();

        for (int i = 0; i < steps.size(); i++) {
            ChainStep step = steps.get(i);
            Map<String, String> params = applyInheritance(step.parameters(), inheritedTopic);
            ToolContext context = new ToolContext(userMessage, conversationId, params, history);

            text.append(stepHeader(i + 1, step)).append("\n");

            if (!step.tool().validate(context)) {
                String error = step.tool().getValidationError(context);
                text.append(error != null ? error : "参数校验失败").append("\n\n");
                continue;
            }

            String response = step.tool().execute(context);
            text.append(response).append("\n\n");
            results.add(new ChainStepResult(step.intent(), params, response));
            inheritedTopic = pickTopic(params, inheritedTopic);
        }

        return new ChainExecution(text.toString().trim(), results);
    }

    /**
     * 顺序执行拆解后的工具链（流式模式）。
     * <p>
     * 每个步骤的输出实时推送，避免多步串行导致前端等待超时；步骤明细通过
     * {@code onComplete} 回调回传，供调用方做后续持久化（如自动保存学习计划）。
     * </p>
     *
     * @param userMessage    用户原始消息
     * @param conversationId 当前会话 ID
     * @param history        历史消息列表
     * @param onComplete     全部步骤完成后的回调，参数为各步骤执行明细
     * @return 流式输出的文本片段
     */
    public Flux<String> executeStream(String userMessage, Integer conversationId, List<Message> history,
                                      Consumer<List<ChainStepResult>> onComplete) {
        List<ChainStep> steps = resolveSteps(userMessage, history);
        if (steps.isEmpty()) {
            return Flux.just("未能识别出多步请求，请分步描述您的需求。");
        }

        List<ChainStepResult> results = new ArrayList<>();
        List<Flux<String>> stepFluxes = new ArrayList<>();
        String inheritedTopic = null;

        for (int i = 0; i < steps.size(); i++) {
            ChainStep step = steps.get(i);
            Map<String, String> params = applyInheritance(step.parameters(), inheritedTopic);
            ToolContext context = new ToolContext(userMessage, conversationId, params, history);
            inheritedTopic = pickTopic(params, inheritedTopic);
            stepFluxes.add(buildStepFlux(step, context, i + 1, results));
        }

        return Flux.concat(stepFluxes)
                .doOnComplete(() -> onComplete.accept(results));
    }

    // 构建单个步骤的流式输出：步骤标题 + 内容 + 分隔空行
    private Flux<String> buildStepFlux(ChainStep step, ToolContext context, int index,
                                       List<ChainStepResult> results) {
        String header = stepHeader(index, step) + "\n";

        if (!step.tool().validate(context)) {
            String error = step.tool().getValidationError(context);
            return Flux.just(header, error != null ? error : "参数校验失败", "\n\n");
        }

        StringBuilder buffer = new StringBuilder();
        return Flux.concat(
                Flux.just(header),
                step.tool().stream(context)
                        .doOnNext(buffer::append)
                        .doOnComplete(() -> results.add(
                                new ChainStepResult(step.intent(), context.getParameters(), buffer.toString())))
                        // 单步失败不影响其余步骤，仅在该步骤位置给出提示
                        .onErrorResume(e -> {
                            log.warn("[Chain] 步骤 {} ({}) 执行失败: {}", index, step.intent(), e.getMessage());
                            return Flux.just("（该步骤生成失败：" + e.getMessage() + "）");
                        }),
                Flux.just("\n\n")
        );
    }

    // 步骤标题，例如【步骤1：SUMMARY】
    private String stepHeader(int index, ChainStep step) {
        return "【步骤" + index + "：" + step.tool().getName() + "】";
    }

    /**
     * 为主题类参数补齐继承值。
     * <p>仅填充空值，不覆盖本步骤已明确给出的主题。</p>
     *
     * @param params         本步骤原始参数
     * @param inheritedTopic 上一步的主题；为空时不做处理
     * @return 补齐后的参数副本
     */
    private Map<String, String> applyInheritance(Map<String, String> params, String inheritedTopic) {
        Map<String, String> merged = new HashMap<>(params == null ? Map.of() : params);
        if (inheritedTopic == null || inheritedTopic.isBlank()) {
            return merged;
        }
        for (String key : TOPIC_KEYS) {
            String value = merged.get(key);
            if (value == null || value.isBlank()) {
                merged.put(key, inheritedTopic);
            }
        }
        return merged;
    }

    // 从参数中取出本步骤的主题，作为下一步的继承来源
    private String pickTopic(Map<String, String> params, String fallback) {
        for (String key : TOPIC_KEYS) {
            String value = params.get(key);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return fallback;
    }

    // 将历史消息裁剪为规划提示词可用的文本
    private String formatHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return "（无历史消息）";
        }
        StringBuilder sb = new StringBuilder();
        int start = Math.max(0, history.size() - 5);
        for (int i = start; i < history.size(); i++) {
            Message msg = history.get(i);
            String content = msg.getContent() == null ? "" : msg.getContent();
            // 助手回复可能很长（如整份测验），截断以控制提示词长度
            if (content.length() > 200) {
                content = content.substring(0, 200) + "…";
            }
            sb.append("user".equals(msg.getRole()) ? "用户" : "助手").append("：").append(content).append("\n");
        }
        return sb.toString();
    }

    // ============================================================
    // 关键词与参数提取（兜底路径）
    // ============================================================

    private boolean containsSummaryKeyword(String message) {
        return message.contains("总结") || message.contains("概括") || message.contains("概要");
    }

    private boolean containsQuizKeyword(String message) {
        return message.contains("题") || message.contains("练习") || message.contains("测试") || message.contains("出题");
    }

    private boolean containsPlanKeyword(String message) {
        return message.contains("计划") || message.contains("安排") || message.contains("规划");
    }

    private boolean containsExplainKeyword(String message) {
        return message.contains("解释") || message.contains("讲解") || message.contains("说明");
    }

    private boolean containsQuestionKeyword(String message) {
        return message.contains("?") || message.contains("？") || message.contains("什么")
            || message.contains("怎么") || message.contains("如何") || message.contains("为什么");
    }

    /**
     * 提取步骤中的主题（兜底路径）。
     * <p>
     * 剥离动词、数量短语与泛化名词后，若没有剩余内容则返回空串，表示"本步骤未指定主题"，
     * 由上层从上一步骤继承。返回空串优于返回"出几道题"这类噪声，否则会把噪声当作
     * 学科名传给下游工具。
     * </p>
     *
     * @param text 步骤原文
     * @return 提取到的主题；未指定时返回空字符串
     */
    private String extractTopic(String text) {
        if (text == null) {
            return "";
        }
        String cleaned = QUANTITY_PATTERN.matcher(text).replaceAll("");
        cleaned = PUNCTUATION_PATTERN.matcher(cleaned).replaceAll("");
        // 首尾修饰词可能连续出现（如"总结一下操作系统的复习内容"），需循环剥离至稳定
        String previous;
        do {
            previous = cleaned;
            cleaned = LEADING_NOISE_PATTERN.matcher(cleaned).replaceAll("");
            cleaned = TRAILING_NOISE_PATTERN.matcher(cleaned).replaceAll("");
        } while (!cleaned.equals(previous));
        return cleaned.trim();
    }

    /**
     * 提取题目数量（兜底路径）。
     *
     * @param text 步骤原文
     * @return 解析出的题量；未明确指定时返回空串，由 {@link #normalizeStep} 统一补默认值
     */
    private String extractCount(String text) {
        Matcher digit = Pattern.compile("(\\d+)\\s*[道个题]").matcher(text);
        if (digit.find()) {
            return digit.group(1);
        }
        Matcher chinese = Pattern.compile("([一二两三四五六七八九十]+)\\s*[道题]").matcher(text);
        if (chinese.find()) {
            return String.valueOf(chineseToNumber(chinese.group(1)));
        }
        // "几道题"这类模糊表述返回空串，由 normalizeStep 统一按默认值处理
        return "";
    }

    /**
     * 中文数字转阿拉伯数字（仅支持十以内，足够覆盖题量场景）。
     *
     * @param chinese 中文数字，如“三”
     * @return 对应的数值；无法识别时返回默认题量
     */
    private int chineseToNumber(String chinese) {
        return switch (chinese) {
            case "一" -> 1;
            case "二", "两" -> 2;
            case "三" -> 3;
            case "四" -> 4;
            case "五" -> 5;
            case "六" -> 6;
            case "七" -> 7;
            case "八" -> 8;
            case "九" -> 9;
            case "十" -> 10;
            default -> DEFAULT_CHAIN_QUIZ_COUNT;
        };
    }

    /**
     * 提取复习天数（兜底路径）。
     *
     * @param text 步骤原文
     * @return 形如 {@code 10天} 的天数；未指定时返回空串
     */
    private String extractDays(String text) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*天").matcher(text);
        if (matcher.find()) {
            return matcher.group(1) + "天";
        }
        return "";
    }

    /**
     * 工具链步骤记录。
     *
     * @param intent 步骤对应的工具意图
     * @param parameters 步骤提取的参数
     * @param tool 实际执行的工具实例
     */
    public record ChainStep(String intent, Map<String, String> parameters, AgentTool tool) {}

    /**
     * 单个步骤的执行明细。
     *
     * @param intent     该步骤的工具意图
     * @param parameters 该步骤实际使用的参数（含继承结果）
     * @param response   该步骤的输出内容
     */
    public record ChainStepResult(String intent, Map<String, String> parameters, String response) {}

    /**
     * 工具链整体执行结果。
     *
     * @param text        拼接后的完整展示文本
     * @param stepResults 各步骤执行明细（校验失败的步骤不会出现在其中）
     */
    public record ChainExecution(String text, List<ChainStepResult> stepResults) {}
}
