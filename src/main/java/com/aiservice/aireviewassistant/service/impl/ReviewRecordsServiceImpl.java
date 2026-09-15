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

        // 统计用户实际复习过的不同课程数
        long reviewedCourseCount = records.stream()
            .map(ReviewRecords::getCourseId)
            .distinct()
            .count();

        UserProgressDto dto = new UserProgressDto();
        dto.setUserId(userId);
        dto.setTotalCourses((int) coursesService.count());
        dto.setReviewedCourses((int) reviewedCourseCount);
        dto.setTotalReviews(records.size());
        dto.setActiveDays(countActiveDays(records));
        dto.setStreakDays(calculateStreakDays(records));
        dto.setLastReviewTime(records.isEmpty() ? null : records.get(records.size() - 1).getCreatedAt());
        dto.setOverallScore(calculateOverallScore(records, reviewedCourseCount, dto.getTotalCourses()));
        return dto;
    }

    /**
     * 统计复习记录覆盖的不同日期数。
     *
     * @param records 复习记录列表
     * @return 活跃天数
     */
    private int countActiveDays(List<ReviewRecords> records) {
        return (int) records.stream()
            .map(r -> r.getCreatedAt().toLocalDate())
            .distinct()
            .count();
    }

    /**
     * 计算连续复习天数（streak）。
     * <p>
     * 从今天向前倒推，只要日期连续则累加；遇到断档即停止。
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
            .map(r -> r.getCreatedAt().toLocalDate())
            .distinct()
            .sorted(Comparator.reverseOrder())
            .collect(Collectors.toList());

        int streak = 0;
        LocalDate today = LocalDate.now();
        for (LocalDate date : dates) {
            // 期望日期为 today, today-1, today-2 ...
            if (date.equals(today.minusDays(streak))) {
                streak++;
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
     * 由课程覆盖率、复习频率、活跃天数、连续天数四部分加权组成，最高 100 分。
     * 无复习记录时返回 0。
     * </p>
     *
     * @param records            复习记录列表
     * @param reviewedCourseCount 用户复习过的课程数
     * @param totalCourses       系统中课程总数
     * @return 综合学习评分（0-100）
     */
    private int calculateOverallScore(List<ReviewRecords> records, long reviewedCourseCount, int totalCourses) {
        if (records == null || records.isEmpty()) {
            return 0;
        }
        int coverageScore = totalCourses > 0 ? (int) (reviewedCourseCount * 100 / totalCourses) : 0;
        int frequencyScore = Math.min(100, records.size() * 5);
        int activeScore = countActiveDays(records) * 10;
        int streakScore = calculateStreakDays(records) * 5;
        int score = coverageScore + frequencyScore + activeScore + streakScore;
        return Math.min(100, score);
    }
}
