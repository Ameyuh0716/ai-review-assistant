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

// 复习记录表 服务实现类
@Service
public class ReviewRecordsServiceImpl extends ServiceImpl<ReviewRecordsMapper, ReviewRecords> implements ReviewRecordsService {

    private final ConversationService conversationService;
    private final CoursesService coursesService;

    public ReviewRecordsServiceImpl(ConversationService conversationService, CoursesService coursesService) {
        this.conversationService = conversationService;
        this.coursesService = coursesService;
    }

    // 保存一次Agent对话产生的复习记录
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
        record.setUserId(userId != null ? userId : conversation.getUserId());
        record.setQuestion(question);
        record.setAnswer(answer);
        record.setCreatedAt(LocalDateTime.now());
        return save(record);
    }

    // 根据课程查询复习记录
    @Override
    public List<ReviewRecords> listByCourse(Integer courseId) {
        return lambdaQuery()
            .eq(ReviewRecords::getCourseId, courseId)
            .orderByDesc(ReviewRecords::getCreatedAt)
            .list();
    }

    // 根据用户查询复习记录
    @Override
    public List<ReviewRecords> listByUser(String userId) {
        return lambdaQuery()
            .eq(ReviewRecords::getUserId, userId)
            .orderByDesc(ReviewRecords::getCreatedAt)
            .list();
    }

    // 查询某用户某课程的学习进度
    @Override
    public StudyProgressDto getStudyProgress(Integer courseId, String userId) {
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
        dto.setLastReviewTime(records.isEmpty() ? null : records.get(records.size() - 1).getCreatedAt());
        dto.setMasteryScore(calculateMasteryScore(records));
        return dto;
    }

    // 查询某用户的整体学习进度总览
    @Override
    public UserProgressDto getUserOverallProgress(String userId) {
        List<ReviewRecords> records = lambdaQuery()
            .eq(ReviewRecords::getUserId, userId)
            .orderByAsc(ReviewRecords::getCreatedAt)
            .list();

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

    // 统计复习覆盖的不同日期数
    private int countActiveDays(List<ReviewRecords> records) {
        return (int) records.stream()
            .map(r -> r.getCreatedAt().toLocalDate())
            .distinct()
            .count();
    }

    // 计算连续复习天数
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
            if (date.equals(today.minusDays(streak))) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    // 基于复习次数、活跃天数、连续天数估算掌握度评分
    private int calculateMasteryScore(List<ReviewRecords> records) {
        int total = records.size();
        int active = countActiveDays(records);
        int streak = calculateStreakDays(records);
        int score = total * 5 + active * 10 + streak * 5;
        return Math.min(100, score);
    }

    // 综合学习评分
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
