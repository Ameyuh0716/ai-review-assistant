package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.service.QuizService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 出题工具。
 * <p>
 * 根据知识点主题为考生生成练习题，支持同步与流式两种输出方式，并可在对话展示层隐藏答案。
 */
@Component
public class QuizTool implements AgentTool {

    /** 测验服务，负责具体的题目生成与格式化。 */
    private final QuizService quizService;

    /**
     * 构造出题工具。
     *
     * @param quizService 测验服务
     */
    public QuizTool(QuizService quizService) {
        this.quizService = quizService;
    }

    /**
     * 返回工具名称 {@code QUIZ}。
     *
     * @return 工具名称
     */
    @Override
    public String getName() {
        return "QUIZ";
    }

    /**
     * 返回工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String getDescription() {
        return "生成练习题";
    }

    /**
     * 返回参数 Schema。
     *
     * @return topic（知识点主题，必填）、count（题目数量，默认1，最大10）
     */
    @Override
    public String getParameterSchema() {
        return "topic（知识点主题，必填）、count（题目数量，默认1，最大10）";
    }

    /**
     * 同步生成练习题。
     *
     * @param context 工具执行上下文
     * @return 生成的练习题文本
     */
    @Override
    public String execute(ToolContext context) {
        String topic = context.getParameters().getOrDefault("topic", extractTopic(context.getUserMessage()));
        int count = parseCount(context.getParameters().get("count"), context.getUserMessage());
        return quizService.generateQuiz(topic, count);
    }

    /**
     * 流式生成练习题。
     *
     * @param context 工具执行上下文
     * @return 流式输出的练习题片段
     */
    @Override
    public Flux<String> stream(ToolContext context) {
        String topic = context.getParameters().getOrDefault("topic", extractTopic(context.getUserMessage()));
        int count = parseCount(context.getParameters().get("count"), context.getUserMessage());
        return quizService.generateQuizStream(topic, count);
    }

    /**
     * 返回适合对话展示的无答案版本（隐藏答案与解析）。
     *
     * @param raw 原始输出文本
     * @param userMessage 用户原始消息，用于提取主题
     * @return 隐藏答案后的展示文本
     */
    public String formatForChat(String raw, String userMessage) {
        String topic = extractTopic(userMessage);
        return quizService.formatQuizOutputForChat(raw, topic);
    }

    /**
     * 对工具原始结果进行展示层格式化。
     *
     * @param rawResponse 工具原始输出
     * @param context 工具执行上下文
     * @return 格式化后的展示文本
     */
    @Override
    public String formatForDisplay(String rawResponse, ToolContext context) {
        return formatForChat(rawResponse, context.getUserMessage());
    }

    /**
     * 校验题目主题与数量是否合法。
     *
     * @param context 工具执行上下文
     * @return 校验通过返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(ToolContext context) {
        String topic = context.getParameters().getOrDefault("topic", extractTopic(context.getUserMessage()));
        int count = parseCount(context.getParameters().get("count"), context.getUserMessage());
        return topic != null && !topic.trim().isEmpty() && count >= 1 && count <= 10;
    }

    /**
     * 获取校验失败时的错误提示。
     *
     * @param context 工具执行上下文
     * @return 校验失败提示；通过时返回 {@code null}
     */
    @Override
    public String getValidationError(ToolContext context) {
        String topic = context.getParameters().getOrDefault("topic", extractTopic(context.getUserMessage()));
        int count = parseCount(context.getParameters().get("count"), context.getUserMessage());
        if (topic == null || topic.trim().isEmpty()) {
            return "请告诉我你想练习哪个知识点或主题。";
        }
        if (count < 1 || count > 10) {
            return "题目数量需要在 1-10 道之间。";
        }
        return null;
    }

    /**
     * 从用户消息中提取题目知识点主题。
     *
     * @param message 用户原始消息
     * @return 提取的主题；无法提取时返回原消息清理后的内容
     */
    private String extractTopic(String message) {
        Matcher aboutMatcher = Pattern.compile("关于(.*?)的").matcher(message);
        if (aboutMatcher.find()) {
            String topic = aboutMatcher.group(1).trim();
            topic = topic.replaceAll("(题|题目|练习|测试|面试).*$", "").trim();
            if (!topic.isEmpty()) {
                return topic;
            }
        }
        return message.replace("生成题目", "")
                      .replace("出题", "")
                      .replace("练习题", "")
                      .replace("测试题", "")
                      .replace("关于", "")
                      .trim();
    }

    /**
     * 解析题目数量，支持数字参数、阿拉伯数字、中文数字及模糊数量。
     *
     * @param countParam 显式传入的数量参数
     * @param message 用户原始消息
     * @return 解析并限制在 1-10 范围内的题目数量
     */
    private int parseCount(String countParam, String message) {
        if (countParam != null && !countParam.isEmpty()) {
            try {
                return clampCount(Integer.parseInt(countParam));
            } catch (NumberFormatException ignored) {
                // 参数非合法数字，继续从消息文本中解析
            }
        }
        // 匹配阿拉伯数字，如"5道题"
        Matcher digitMatcher = Pattern.compile("(\\d+)\\s*[道个题]").matcher(message);
        if (digitMatcher.find()) {
            return clampCount(Integer.parseInt(digitMatcher.group(1)));
        }
        // 匹配中文数字，如"三道题"；不匹配量词"个"，避免把"设计一个计划"里的"一个"误读成 1 道题
        Matcher cnMatcher = Pattern.compile("([一二两三四五六七八九十]+)\\s*[道题]").matcher(message);
        if (cnMatcher.find()) {
            return clampCount(chineseToNumber(cnMatcher.group(1)));
        }
        // 模糊数量统一按 3 道处理
        if (Pattern.compile("几\\s*[道个题]").matcher(message).find() || message.contains("一些")) {
            return 3;
        }
        return 1;
    }

    private int clampCount(int count) {
        return Math.max(1, Math.min(count, 10));
    }

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
            default -> 1;
        };
    }
}
