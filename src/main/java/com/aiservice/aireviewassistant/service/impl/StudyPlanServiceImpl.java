package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.entity.StudyPlan;
import com.aiservice.aireviewassistant.mapper.StudyPlanMapper;
import com.aiservice.aireviewassistant.service.StudyPlanService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学习计划服务实现类。
 * <p>
 * 实现 {@link StudyPlanService} 接口，提供学习计划的保存与查询功能。
 * 保存时会对输入进行简单的规范化处理，保证前端展示格式统一。
 * </p>
 */
@Service
public class StudyPlanServiceImpl extends ServiceImpl<StudyPlanMapper, StudyPlan> implements StudyPlanService {

    /**
     * 保存一条学习计划。
     * <p>
     * 对课程名与可用天数做去空处理；若可用天数为纯数字，则自动追加“天”单位，
     * 例如 "5" 会规范为 "5天"。创建时间与更新时间均设为当前时间。
     * </p>
     *
     * @param userId        用户 ID
     * @param courseName    课程名称
     * @param availableDays 可用天数描述
     * @param content       学习计划内容
     * @return 保存后的学习计划实体
     */
    @Override
    public StudyPlan savePlan(Integer userId, String courseName, String availableDays, String content) {
        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        plan.setCourseName(courseName != null ? courseName.trim() : "");
        String days = availableDays != null ? availableDays.trim() : "";
        // 规范化天数显示：纯数字自动补全为 "X天"，保持前端展示一致性
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

    /**
     * 根据用户 ID 查询学习计划，按创建时间倒序返回。
     *
     * @param userId 用户 ID
     * @return 学习计划列表
     */
    @Override
    public List<StudyPlan> listByUserId(Integer userId) {
        LambdaQueryWrapper<StudyPlan> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(StudyPlan::getUserId, userId);
        wrapper.orderByDesc(StudyPlan::getCreatedAt);
        return list(wrapper);
    }
}
