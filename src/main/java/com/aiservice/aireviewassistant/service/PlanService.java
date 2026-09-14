package com.aiservice.aireviewassistant.service;

import reactor.core.publisher.Flux;

// 复习计划服务接口
public interface PlanService {

    // 根据课程和可用时间生成复习计划
    String createPlan(String courseName, String availableDays);

    // 流式生成复习计划
    Flux<String> createPlanStream(String courseName, String availableDays);
}
