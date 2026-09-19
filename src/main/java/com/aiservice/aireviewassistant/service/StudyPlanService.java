package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.dto.PlanDayDto;
import com.aiservice.aireviewassistant.entity.StudyPlan;
import com.baomidou.mybatisplus.extension.service.IService;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 学习计划服务接口。
 * <p>
 * 负责保存并查询用户的学习计划。学习计划通常由 AI Agent 根据课程名称、可用天数与复习内容生成。
 * 除基础 CRUD 外，还提供结构化支持：从正文解析每日结构、保存每日勾选进度，
 * 以及按天按环节（复习内容/掌握内容/练习）流式生成学习材料。
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
     * <p>返回前会自动解析正文并填充每日结构（{@link StudyPlan#getDays()}）。</p>
     *
     * @param userId 用户 ID
     * @return 学习计划列表
     */
    List<StudyPlan> listByUserId(Integer userId);

    /**
     * 从计划 Markdown 正文中解析每日结构。
     * <p>
     * 支持多种日程标记变体：{@code ## 第1天：标题}、{@code 1. 第1天：标题}、{@code **Day 1** 标题} 等；
     * 每收集到下一个日程标记前的列表项与正文行作为该日要点。无法识别日程时返回空列表。
     * </p>
     *
     * @param content 计划正文
     * @return 每日结构列表（按天数升序）
     */
    List<PlanDayDto> parseDays(String content);

    /**
     * 更新计划的结构化进度。
     *
     * @param userId   用户 ID（用于归属校验）
     * @param planId   计划 ID
     * @param progress 进度 JSON 字符串
     * @return 更新后的计划；计划不存在或不属于该用户时返回 null
     */
    StudyPlan updateProgress(Integer userId, Integer planId, String progress);

    /**
     * 流式生成某天某环节的学习材料（复习内容 / 掌握内容 / 练习）。
     * <p>
     * 生成完成后将内容自动写入计划的结构化进度中持久化，刷新页面后仍可查看。
     * 环节取值：{@code review}（复习内容）、{@code mastery}（掌握内容）、{@code practice}（练习）。
     * </p>
     *
     * @param userId  用户 ID（用于归属校验）
     * @param planId  计划 ID
     * @param day     天数序号
     * @param section 环节名称
     * @return 流式文本；校验失败时返回一次性提示文本
     */
    Flux<String> generateDaySection(Integer userId, Integer planId, Integer day, String section);
}
