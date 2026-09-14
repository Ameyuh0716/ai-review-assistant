package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.StudyPlan;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

// 学习计划服务接口
public interface StudyPlanService extends IService<StudyPlan> {

    // 保存学习计划
    StudyPlan savePlan(Integer userId, String courseName, String availableDays, String content);

    // 查询当前用户的学习计划列表（按创建时间倒序）
    List<StudyPlan> listByUserId(Integer userId);
}
