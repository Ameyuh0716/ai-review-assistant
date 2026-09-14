package com.aiservice.aireviewassistant.agent.tool;

import com.aiservice.aireviewassistant.service.PlanService;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

// 复习计划工具：制定课程复习计划
@Component
public class PlanTool implements AgentTool {

    private final PlanService planService;

    public PlanTool(PlanService planService) {
        this.planService = planService;
    }

    @Override
    public String getName() {
        return "PLAN";
    }

    @Override
    public String getDescription() {
        return "制定复习计划";
    }

    @Override
    public String getParameterSchema() {
        return "courseName（课程名，必填）、availableDays（可用天数，默认7天）";
    }

    @Override
    public String execute(ToolContext context) {
        String courseName = context.getParameters().getOrDefault("courseName", extractCourseName(context.getUserMessage()));
        String days = context.getParameters().getOrDefault("availableDays", extractDays(context.getUserMessage()));
        return planService.createPlan(courseName, days);
    }

    @Override
    public Flux<String> stream(ToolContext context) {
        String courseName = context.getParameters().getOrDefault("courseName", extractCourseName(context.getUserMessage()));
        String days = context.getParameters().getOrDefault("availableDays", extractDays(context.getUserMessage()));
        return planService.createPlanStream(courseName, days);
    }

    @Override
    public boolean validate(ToolContext context) {
        String courseName = context.getParameters().getOrDefault("courseName", extractCourseName(context.getUserMessage()));
        return courseName != null && !courseName.trim().isEmpty();
    }

    @Override
    public String getValidationError(ToolContext context) {
        String courseName = context.getParameters().getOrDefault("courseName", extractCourseName(context.getUserMessage()));
        if (courseName == null || courseName.trim().isEmpty()) {
            return "请告诉我你想为哪门课程制定复习计划。";
        }
        return null;
    }

    // 提取课程名
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

    // 提取天数
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
