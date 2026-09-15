package com.aiservice.aireviewassistant.service;

import reactor.core.publisher.Flux;

/**
 * 复习计划服务接口。
 *
 * <p>根据课程名称与用户可用天数，调用 AI 模型生成个性化的复习计划。
 * 提供同步返回完整计划文本与流式返回（SSE）两种方式，供不同交互场景使用。</p>
 */
public interface PlanService {

    /**
     * 根据课程和可用时间生成完整复习计划。
     *
     * @param courseName    课程名称，用于指定需要复习的科目
     * @param availableDays 用户可用于复习的天数，通常为自然语言描述或数字
     * @return AI 生成的复习计划文本
     */
    String createPlan(String courseName, String availableDays);

    /**
     * 流式生成复习计划。
     *
     * @param courseName    课程名称，用于指定需要复习的科目
     * @param availableDays 用户可用于复习的天数，通常为自然语言描述或数字
     * @return 按流式分片返回的复习计划文本
     */
    Flux<String> createPlanStream(String courseName, String availableDays);
}
