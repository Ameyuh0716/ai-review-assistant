package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.entity.StudyPlan;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 学习计划服务接口。
 * <p>
 * 负责保存并查询用户的学习计划。学习计划通常由 AI Agent 根据课程名称、可用天数与复习内容生成。
 * 边界：仅处理 {@link StudyPlan} 实体的持久化与列表查询，不包含计划生成逻辑。
 * </p>
 */
public interface StudyPlanService extends IService<StudyPlan> {

    /**
     * 保存一条学习计划。
     * <p>
     * 会对课程名和可用天数进行 trim 处理；若可用天数为纯数字，则自动追加“天”单位。
     * </p>
     *
     * @param userId       用户 ID
     * @param courseName   课程名称
     * @param availableDays 可用天数描述，例如 "5" 或 "5天"
     * @param content      学习计划正文内容
     * @return 保存后的学习计划实体
     */
    StudyPlan savePlan(Integer userId, String courseName, String availableDays, String content);

    /**
     * 查询指定用户的学习计划列表，按创建时间倒序排列。
     *
     * @param userId 用户 ID
     * @return 学习计划列表
     */
    List<StudyPlan> listByUserId(Integer userId);
}
