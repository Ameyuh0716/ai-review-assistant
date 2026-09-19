package com.aiservice.aireviewassistant.service.impl;

import com.aiservice.aireviewassistant.dto.StudyProgressDto;
import com.aiservice.aireviewassistant.dto.UserProgressDto;
import com.aiservice.aireviewassistant.entity.Conversation;
import com.aiservice.aireviewassistant.entity.Courses;
import com.aiservice.aireviewassistant.entity.ReviewRecords;
import com.aiservice.aireviewassistant.mapper.ReviewRecordsMapper;
import com.aiservice.aireviewassistant.service.ConversationService;
import com.aiservice.aireviewassistant.service.CoursesService;
import com.aiservice.aireviewassistant.service.ReviewRecordsService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 复习记录服务实现类。
 * <p>
 * 实现 {@link ReviewRecordsService} 中定义的复习记录持久化与进度统计逻辑。
 * 依赖 {@link ConversationService} 解析会话对应的课程与用户，依赖 {@link CoursesService} 获取课程名称及总课程数。
 * </p>
 */
@Service
public class ReviewRecordsServiceImpl extends ServiceImpl<ReviewRecordsMapper, ReviewRecords> implements ReviewRecordsService {

    private final ConversationService conversationService;
    private final CoursesService coursesService;

    /**
     * 构造器注入依赖。
     *
     * @param conversationService 会话服务，用于根据会话 ID 查询课程信息
     * @param coursesService      课程服务，用于获取课程元数据
     */
    public ReviewRecordsServiceImpl(ConversationService conversationService, CoursesService coursesService) {
        this.conversationService = conversationService;
        this.coursesService = coursesService;
    }

    /**
     * 保存一次 Agent 对话产生的复习记录。
     * <p>
     * 通过会话 ID 查询课程 ID，若 caller 未传入 userId，则回退到会话归属用户。
     * </p>
     *
     * @param conversationId 会话 ID
     * @param userId         用户 ID
     * @param question       复习问题
     * @param answer         AI 回答
     * @return 是否保存成功
     */
    @Override
    public boolean saveFromAgent(Integer conversationId, String userId, String question, String answer) {
        if (conversationId == null) {
            return false;
        }
        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            return false;
        }
        ReviewRecords record = new ReviewRecords();
        record.setConversationId(conversationId);
        record.setCourseId(conversation.getCourseId());
        // 优先使用传入的 userId，否则使用会话关联的用户
        record.setUserId(userId != null ? userId : conversation.getUserId());
        record.setQuestion(question);
        record.setAnswer(answer);
        record.setCreatedAt(LocalDateTime.now());
        return save(record);
    }

    /**
     * 根据课程查询复习记录，按创建时间倒序返回。
     *
     * @param courseId 课程 ID
     * @return 复习记录列表
     */
    @Override
    public List<ReviewRecords> listByCourse(Integer courseId) {
        return lambdaQuery()
            .eq(ReviewRecords::getCourseId, courseId)
            .orderByDesc(ReviewRecords::getCreatedAt)
            .list();
    }

    /**
     * 根据用户查询复习记录，按创建时间倒序返回。
     *
     * @param userId 用户 ID
     * @return 复习记录列表
     */
    @Override
    public List<ReviewRecords> listByUser(String userId) {
        return lambdaQuery()
            .eq(ReviewRecords::getUserId, userId)
            .orderByDesc(ReviewRecords::getCreatedAt)
            .list();
    }

    /**
     * 计算指定用户在指定课程下的学习进度。
     * <p>
     * 聚合指标包括总复习次数、活跃天数、连续天数、最近复习时间以及掌握度评分。
     * 课程名称为空时显示“未知课程”。
     * </p>
     *
     * @param courseId 课程 ID
     * @param userId   用户 ID
     * @return 学习进度 DTO
     */
    @Override
    public StudyProgressDto getStudyProgress(Integer courseId, String userId) {
        // 按时间升序拉取该用户该课程的全部复习记录，便于计算 streak 与最近复习时间
        List<ReviewRecords> records = lambdaQuery()
            .eq(ReviewRecords::getCourseId, courseId)
            .eq(ReviewRecords::getUserId, userId)
            .orderByAsc(ReviewRecords::getCreatedAt)
            .list();

        Courses course = coursesService.getById(courseId);

        StudyProgressDto dto = new StudyProgressDto();
        dto.setCourseId(courseId);
        dto.setCourseName(course != null ? course.getName() : "未知课程");
        dto.setTotalReviews(records.size());
        dto.setActiveDays(countActiveDays(records));
        dto.setStreakDays(calculateStreakDays(records));
        // 记录已按时间升序排列，最后一条即为最近复习记录
        dto.setLastReviewTime(records.isEmpty() ? null : records.get(records.size() - 1).getCreatedAt());
        dto.setMasteryScore(calculateMasteryScore(records));
        return dto;
    }

    /**
     * 计算指定用户的整体学习进度总览。
     * <p>
     * 统计用户复习过的课程数量、总复习次数、活跃天数、连续天数及综合学习评分。
     * 综合评分综合考虑课程覆盖率、复习频率、活跃度和连续性。
     * </p>
     *
     * @param userId 用户 ID
     * @return 用户整体进度 DTO
     */
    @Override
    public UserProgressDto getUserOverallProgress(String userId) {
        List<ReviewRecords> records = lambdaQuery()
            .eq(ReviewRecords::getUserId, userId)
            .orderByAsc(ReviewRecords::getCreatedAt)
            .list();

        // 统计用户实际复习过的不同课程数（courseId 可为空，空值不计入课程覆盖）
        long reviewedCourseCount = records.stream()
            .map(ReviewRecords::getCourseId)
            .filter(java.util.Objects::nonNull)
            .distinct()
            .count();

        UserProgressDto dto = new UserProgressDto();
        dto.setUserId(userId);
        dto.setTotalCourses(countUserCourses(userId));
        dto.setReviewedCourses((int) reviewedCourseCount);
        dto.setTotalReviews(records.size());
        dto.setActiveDays(countActiveDays(records));
        dto.setStreakDays(calculateStreakDays(records));
        dto.setLastReviewTime(records.isEmpty() ? null : records.get(records.size() - 1).getCreatedAt());
        dto.setOverallScore(calculateOverallScore(records, reviewedCourseCount, dto.getTotalCourses()));
        return dto;
    }

    /**
     * 统计用户拥有的课程总数。
     * <p>userId 为数字字符串时按用户统计本人课程；匿名或非数字时回退到系统课程总数，
     * 避免课程覆盖率评分失真。</p>
     *
     * @param userId 用户标识（数字字符串或 anonymous）
     * @return 课程总数
     */
    private int countUserCourses(String userId) {
        try {
            int uid = Integer.parseInt(userId);
            Long count = coursesService.lambdaQuery().eq(Courses::getUserId, uid).count();
            return count != null ? count.intValue() : 0;
        } catch (NumberFormatException e) {
            return (int) coursesService.count();
        }
    }

    /**
     * 统计复习记录覆盖的不同日期数。
     *
     * @param records 复习记录列表
     * @return 活跃天数
     */
    private int countActiveDays(List<ReviewRecords> records) {
        return (int) records.stream()
            .map(ReviewRecords::getCreatedAt)
            .filter(java.util.Objects::nonNull)
            .map(LocalDateTime::toLocalDate)
            .distinct()
            .count();
    }

    /**
     * 计算连续复习天数（streak）。
     * <p>
     * 从今天（若今天无记录则从昨天）向前倒推，只要日期连续则累加；遇到断档即停止。
     * 同一天多次复习只计一天。
     * </p>
     *
     * @param records 复习记录列表
     * @return 连续复习天数
     */
    private int calculateStreakDays(List<ReviewRecords> records) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        List<LocalDate> dates = records.stream()
            .map(ReviewRecords::getCreatedAt)
            .filter(java.util.Objects::nonNull)
            .map(LocalDateTime::toLocalDate)
            .distinct()
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());
        if (dates.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        // 保护连续：今天没有复习时允许从昨天开始计算；若最新记录早于昨天则已断档
        LocalDate latest = dates.get(0);
        LocalDate cursor;
        if (latest.equals(today)) {
            cursor = today;
        } else if (latest.equals(today.minusDays(1))) {
            cursor = today.minusDays(1);
        } else {
            return 0;
        }

        int streak = 0;
        for (LocalDate date : dates) {
            if (date.equals(cursor)) {
                streak++;
                cursor = cursor.minusDays(1);
            } else {
                break;
            }
        }
        return streak;
    }

    /**
     * 估算课程掌握度评分。
     * <p>
     * 基于复习次数、活跃天数、连续天数加权求和，最高 100 分。
     * </p>
     *
     * @param records 复习记录列表
     * @return 掌握度评分（0-100）
     */
    private int calculateMasteryScore(List<ReviewRecords> records) {
        int total = records.size();
        int active = countActiveDays(records);
        int streak = calculateStreakDays(records);
        int score = total * 5 + active * 10 + streak * 5;
        return Math.min(100, score);
    }

    /**
     * 计算综合学习评分。
     * <p>
     * 四个维度加权组成，最高 100 分：
     * 课程覆盖率（30）+ 复习频率（30）+ 活跃天数（20）+ 连续天数（20）。
     * 无复习记录时返回 0。
     * </p>
     *
     * @param records            复习记录列表
     * @param reviewedCourseCount 用户复习过的课程数
     * @param totalCourses       用户课程总数
     * @return 综合学习评分（0-100）
     */
    private int calculateOverallScore(List<ReviewRecords> records, long reviewedCourseCount, int totalCourses) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        // 课程覆盖率：已复习课程 / 课程总数，最高 30 分
        int coverageScore = totalCourses > 0 ? (int) Math.min(30, reviewedCourseCount * 30 / totalCourses) : 0;
        // 复习频率：每次复习 3 分，最高 30 分
        int frequencyScore = Math.min(30, records.size() * 3);
        // 活跃天数：每天 4 分，最高 20 分
        int activeScore = Math.min(20, countActiveDays(records) * 4);
        // 连续天数：每天 5 分，最高 20 分
        int streakScore = Math.min(20, calculateStreakDays(records) * 5);
        int score = coverageScore + frequencyScore + activeScore + streakScore;
        return Math.min(100, score);
    }
}
