package com.aiservice.aireviewassistant.service;

import com.aiservice.aireviewassistant.dto.StudyProgressDto;
import com.aiservice.aireviewassistant.dto.UserProgressDto;
import com.aiservice.aireviewassistant.entity.ReviewRecords;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
 * 复习记录服务接口。
 * <p>
 * 负责管理用户与 AI Agent 对话产生的复习记录，并基于复习行为计算学习进度、连续天数、掌握度等统计指标。
 * 边界：仅处理 {@link ReviewRecords} 实体的读写与聚合统计，不直接参与会话管理或课程内容的维护。
 * </p>
 */
public interface ReviewRecordsService extends IService<ReviewRecords> {

    /**
     * 保存一次 Agent 对话产生的复习记录，并根据会话自动关联课程。
     *
     * @param conversationId 会话 ID，用于查询对应的课程信息；为空时直接返回 false
     * @param userId         用户 ID；为空时会话归属用户
     * @param question       复习问题内容
     * @param answer         AI 回答内容
     * @return 是否保存成功
     */
    boolean saveFromAgent(Integer conversationId, String userId, String question, String answer);

    /**
     * 根据课程查询复习记录，按创建时间倒序排列。
     *
     * @param courseId 课程 ID
     * @return 该课程的复习记录列表
     */
    List<ReviewRecords> listByCourse(Integer courseId);

    /**
     * 根据用户查询复习记录，按创建时间倒序排列。
     *
     * @param userId 用户 ID
     * @return 该用户的复习记录列表
     */
    List<ReviewRecords> listByUser(String userId);

    /**
     * 查询指定用户在指定课程下的学习进度。
     * <p>
     * 统计维度包括：总复习次数、活跃天数、连续复习天数、最近复习时间以及基于复习行为的掌握度评分。
     * </p>
     *
     * @param courseId 课程 ID
     * @param userId   用户 ID
     * @return 课程学习进度 DTO
     */
    StudyProgressDto getStudyProgress(Integer courseId, String userId);

    /**
     * 查询指定用户的整体学习进度总览。
     * <p>
     * 统计维度包括：课程覆盖数、总复习次数、活跃天数、连续复习天数、最近复习时间以及综合学习评分。
     * </p>
     *
     * @param userId 用户 ID
     * @return 用户整体学习进度 DTO
     */
    UserProgressDto getUserOverallProgress(String userId);
}
