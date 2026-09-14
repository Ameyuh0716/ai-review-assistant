package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.StudyPlan;
import com.aiservice.aireviewassistant.mapper.StudyPlanMapper;
import com.aiservice.aireviewassistant.service.StudyPlanService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

// 学习计划服务实现
@Service
public class StudyPlanServiceImpl extends ServiceImpl<StudyPlanMapper, StudyPlan> implements StudyPlanService {

    @Override
    public StudyPlan savePlan(Integer userId, String courseName, String availableDays, String content) {
        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        plan.setCourseName(courseName != null ? courseName.trim() : "");
        String days = availableDays != null ? availableDays.trim() : "";
        // 规范化天数显示，如 "5" → "5天"
        if (days.matches("\\d+")) {
            days = days + "天";
        }
        plan.setAvailableDays(days);
        plan.setContent(content);
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        save(plan);
        return plan;
    }

    @Override
    public List<StudyPlan> listByUserId(Integer userId) {
        LambdaQueryWrapper<StudyPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyPlan::getUserId, userId);
        wrapper.orderByDesc(StudyPlan::getCreatedAt);
        return list(wrapper);
    }
}
