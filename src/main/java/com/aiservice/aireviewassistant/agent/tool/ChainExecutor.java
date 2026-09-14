package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.entity.Message;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 工具链执行器：处理"先...再...然后..."形式的多步请求
@Slf4j
@Component
public class ChainExecutor {

    private final ToolRegistry toolRegistry;

    public ChainExecutor(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    // 判断是否为链式请求：必须同时包含"先"和至少一个后续连接词
    public boolean isChainRequest(String message) {
        if (!message.contains("先")) return false;
        // 排除"先说"、"先问"等非工具链用法
        String cleaned = message.replaceAll("先说|先问|先聊|先谈|先讲", "");
        if (!cleaned.contains("先")) return false;
        return cleaned.contains("再") || cleaned.contains("然后") || cleaned.contains("接着");
    }

    // 解析链式请求为多个步骤
    public List<ChainStep> parseChain(String message) {
        List<ChainStep> steps = new ArrayList<>();
        String remaining = message.trim();

        // 必须以"先"开头
        if (!remaining.startsWith("先")) {
            return steps;
        }
        remaining = remaining.substring(1).trim();

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
            if (steps.size() >= 3) {
                break;
            }
        }

        return steps;
    }

    // 查找第一个链式分隔符
    private Separator findFirstSeparator(String text) {
        String[] separators = {"再", "然后", "接着"};
        int firstIndex = -1;
        String firstSep = null;
        for (String sep : separators) {
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

    // 执行工具链，支持主题继承
    public String executeChain(String userMessage, Integer conversationId, List<Message> history) {
        List<ChainStep> steps = parseChain(userMessage);
        if (steps.isEmpty()) {
            return "未能识别出多步请求，请分步描述您的需求。";
        }

        String inheritedTopic = null;
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            ChainStep step = steps.get(i);
            Map<String, String> params = new java.util.HashMap<>(step.parameters());

            // 如果当前步骤缺少主题，继承上一步的主题
            if (inheritedTopic != null && !inheritedTopic.isEmpty()) {
                if (!params.containsKey("topic") || params.get("topic") == null || params.get("topic").isEmpty()) {
                    params.put("topic", inheritedTopic);
                }
                if (!params.containsKey("courseName") || params.get("courseName") == null || params.get("courseName").isEmpty()) {
                    params.put("courseName", inheritedTopic);
                }
                if (!params.containsKey("concept") || params.get("concept") == null || params.get("concept").isEmpty()) {
                    params.put("concept", inheritedTopic);
                }
            }

            ToolContext context = new ToolContext(userMessage, conversationId, params, history);
            if (!step.tool().validate(context)) {
                String error = step.tool().getValidationError(context);
                result.append("【步骤").append(i + 1).append("：").append(step.tool().getName()).append("】\n")
                      .append(error != null ? error : "参数校验失败").append("\n\n");
                continue;
            }

            String response = step.tool().execute(context);
            result.append("【步骤").append(i + 1).append("：").append(step.tool().getName()).append("】\n")
                  .append(response).append("\n\n");

            // 继承主题供下一步使用
            if (params.containsKey("topic") && params.get("topic") != null && !params.get("topic").isEmpty()) {
                inheritedTopic = params.get("topic");
            } else if (params.containsKey("courseName") && params.get("courseName") != null && !params.get("courseName").isEmpty()) {
                inheritedTopic = params.get("courseName");
            } else if (params.containsKey("concept") && params.get("concept") != null && !params.get("concept").isEmpty()) {
                inheritedTopic = params.get("concept");
            }
        }

        return result.toString().trim();
    }

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

    private String extractTopic(String text) {
        String cleaned = text.replaceAll("^(先|再|然后|接着|请|帮我|给我|生成|来|来一段)?\\s*", "")
                             .replaceAll("(总结|概括|概要|出题|生成|练习|测试|解释|讲解|说明|计划|安排|规划)(?:一下|一份)?\\s*", "")
                             .replaceAll("\\d+\\s*[道个题]", "")
                             .replaceAll("\\d+\\s*天", "")
                             .replaceAll("[，,。.;；?？]", "")
                             .trim();
        return cleaned.isEmpty() ? "" : cleaned;
    }

    private String extractCount(String text) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*[道个题]").matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "1";
    }

    private String extractDays(String text) {
        Matcher matcher = Pattern.compile("(\\d+)\\s*天").matcher(text);
        if (matcher.find()) {
            return matcher.group(1) + "天";
        }
        return "7天";
    }

    // 工具链步骤记录
    public record ChainStep(String intent, Map<String, String> parameters, AgentTool tool) {}
}
