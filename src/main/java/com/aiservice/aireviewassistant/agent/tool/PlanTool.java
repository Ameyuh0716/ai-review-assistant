package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.service.PlanService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 复习计划工具。
 * <p>
 * 根据课程名称与可用天数为考生制定复习计划，支持同步与流式两种输出方式。
 */
@Component
public class PlanTool implements AgentTool {

    /** 计划服务，负责具体的复习计划生成。 */
    private final PlanService planService;

    /**
     * 构造复习计划工具。
     *
     * @param planService 计划服务
     */
    public PlanTool(PlanService planService) {
        this.planService = planService;
    }

    /**
     * 返回工具名称 {@code PLAN}。
     *
     * @return 工具名称
     */
    @Override
    public String getName() {
        return "PLAN";
    }

    /**
     * 返回工具描述。
     *
     * @return 工具描述
     */
    @Override
    public String getDescription() {
        return "制定复习计划";
    }

    /**
     * 返回参数 Schema。
     *
     * @return courseName（课程名，必填）、availableDays（可用天数，默认7天）
     */
    @Override
    public String getParameterSchema() {
        return "courseName（课程名，必填）、availableDays（可用天数，默认7天）";
    }

    /**
     * 同步制定复习计划。
     *
     * @param context 工具执行上下文
     * @return 生成的复习计划文本
     */
    @Override
    public String execute(ToolContext context) {
        String courseName = context.getParameters().getOrDefault("courseName", extractCourseName(context.getUserMessage()));
        String days = context.getParameters().getOrDefault("availableDays", extractDays(context.getUserMessage()));
        return planService.createPlan(courseName, days);
    }

    /**
     * 流式制定复习计划。
     *
     * @param context 工具执行上下文
     * @return 流式输出的计划片段
     */
    @Override
    public Flux<String> stream(ToolContext context) {
        String courseName = context.getParameters().getOrDefault("courseName", extractCourseName(context.getUserMessage()));
        String days = context.getParameters().getOrDefault("availableDays", extractDays(context.getUserMessage()));
        return planService.createPlanStream(courseName, days);
    }

    /**
     * 校验课程名是否为空。
     *
     * @param context 工具执行上下文
     * @return 校验通过返回 {@code true}，否则返回 {@code false}
     */
    @Override
    public boolean validate(ToolContext context) {
        String courseName = context.getParameters().getOrDefault("courseName", extractCourseName(context.getUserMessage()));
        return courseName != null && !courseName.trim().isEmpty();
    }

    /**
     * 获取校验失败时的错误提示。
     *
     * @param context 工具执行上下文
     * @return 校验失败提示；通过时返回 {@code null}
     */
    @Override
    public String getValidationError(ToolContext context) {
        String courseName = context.getParameters().getOrDefault("courseName", extractCourseName(context.getUserMessage()));
        if (courseName == null || courseName.trim().isEmpty()) {
            return "请告诉我你想为哪门课程制定复习计划。";
        }
        return null;
    }

    /**
     * 从用户消息中提取课程名。
     *
     * @param message 用户原始消息
     * @return 提取的课程名；无法提取时返回 {@code null}
     */
    private String extractCourseName(String message) {
        Matcher aboutMatcher = Pattern.compile("关于(.*?)的").matcher(message);
        if (aboutMatcher.find()) {
            String course = aboutMatcher.group(1).trim();
            course = course.replaceAll("(复习计划|计划|安排).*$", "").trim();
            if (!course.isEmpty()) {
                return course;
            }
        }
        // 清理常见指令性词汇，提取用户实际提到的科目
        String cleaned = message.replace("请帮我制定", "")
                      .replace("我想制定", "")
                      .replace("帮我", "")
                      .replace("制定", "")
                      .replace("复习计划", "")
                      .replace("学习计划", "")
                      .replace("计划", "")
                      .replace("关于", "")
                      .trim();
        // 去掉末尾的「的」、标点以及天数描述
        cleaned = cleaned.replaceAll("的[\\s，,。\\.]?\\d*\\s*天?$", "")
                         .replaceAll("[\\s，,。\\.]+$", "")
                         .trim();
        if (!cleaned.isEmpty() && cleaned.length() >= 2) {
            return cleaned;
        }
        return null;
    }

    /**
     * 从用户消息中提取可用天数，默认返回 7 天。
     *
     * @param message 用户原始消息
     * @return 带单位的天数字符串
     */
    private String extractDays(String message) {
        Matcher dayMatcher = Pattern.compile("(\\d+)\\s*天").matcher(message);
        if (dayMatcher.find()) {
            return dayMatcher.group(1) + "天";
        }
        if (message.contains("两周") || message.contains("两周")) {
            return "14天";
        }
        return "7天";
    }
}
