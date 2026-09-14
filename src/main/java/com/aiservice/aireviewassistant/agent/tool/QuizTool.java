package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.service.QuizService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 出题工具：根据知识点生成练习题
@Component
public class QuizTool implements AgentTool {

    private final QuizService quizService;

    public QuizTool(QuizService quizService) {
        this.quizService = quizService;
    }

    @Override
    public String getName() {
        return "QUIZ";
    }

    @Override
    public String getDescription() {
        return "生成练习题";
    }

    @Override
    public String getParameterSchema() {
        return "topic（知识点主题，必填）、count（题目数量，默认1，最大10）";
    }

    @Override
    public String execute(ToolContext context) {
        String topic = context.getParameters().getOrDefault("topic", extractTopic(context.getUserMessage()));
        int count = parseCount(context.getParameters().get("count"), context.getUserMessage());
        return quizService.generateQuiz(topic, count);
    }

    @Override
    public Flux<String> stream(ToolContext context) {
        String topic = context.getParameters().getOrDefault("topic", extractTopic(context.getUserMessage()));
        int count = parseCount(context.getParameters().get("count"), context.getUserMessage());
        return quizService.generateQuizStream(topic, count);
    }

    /**
     * 返回适合对话展示的无答案版本（隐藏答案与解析）。
     */
    public String formatForChat(String raw, String userMessage) {
        String topic = extractTopic(userMessage);
        return quizService.formatQuizOutputForChat(raw, topic);
    }

    @Override
    public String formatForDisplay(String rawResponse, ToolContext context) {
        return formatForChat(rawResponse, context.getUserMessage());
    }

    @Override
    public boolean validate(ToolContext context) {
        String topic = context.getParameters().getOrDefault("topic", extractTopic(context.getUserMessage()));
        int count = parseCount(context.getParameters().get("count"), context.getUserMessage());
        return topic != null && !topic.trim().isEmpty() && count >= 1 && count <= 10;
    }

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

    // 从消息中提取题目知识点
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

    // 解析题目数量
    private int parseCount(String countParam, String message) {
        if (countParam != null && !countParam.isEmpty()) {
            try {
                return clampCount(Integer.parseInt(countParam));
            } catch (NumberFormatException ignored) {
            }
        }
        Matcher digitMatcher = Pattern.compile("(\\d+)\\s*[道个题]").matcher(message);
        if (digitMatcher.find()) {
            return clampCount(Integer.parseInt(digitMatcher.group(1)));
        }
        Matcher cnMatcher = Pattern.compile("([一二两三四五六七八九十]+)\\s*[道个题]").matcher(message);
        if (cnMatcher.find()) {
            return clampCount(chineseToNumber(cnMatcher.group(1)));
        }
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
