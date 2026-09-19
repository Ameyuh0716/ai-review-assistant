package com.aiservice.aireviewassistant.dto;

import java.util.List;

/**
 * 学习计划的单日结构。
 * <p>
 * 由后端从计划 Markdown 正文中解析得出，供前端渲染"按天"的结构化视图：
 * 每一天包含序号、标题（主题）与若干要点条目。
 * </p>
 *
 * @param day   天数序号（1 开始）
 * @param title 当日主题，例如 "网络体系结构"
 * @param items 当日任务要点列表
 */
public record PlanDayDto(Integer day, String title, List<String> items) {
}
